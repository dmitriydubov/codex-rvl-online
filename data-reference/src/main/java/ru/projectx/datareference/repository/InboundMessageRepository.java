package ru.projectx.datareference.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;

/** Сырые сообщения 1С в таблице {@code inbound_message}: выборки разбора и задач по расписанию. */
@Repository
public interface InboundMessageRepository extends JpaRepository<InboundMessage, UUID> {

    /**
     * Сообщения, которые пора разбирать, в порядке приёма: версий у сообщений 1С нет, и поток применяется по порядку.
     * Отложенное до повтора сообщение держит более поздние сообщения своего потока, а ожидающее родителя попадает сюда,
     * когда родитель появился или признан не нашим. Строки не блокируются: сервис работает одной копией.
     */
    @Query("""
            select message
              from InboundMessage message
             where (message.status = NEW
                    or (message.status = RETRY and message.nextRetryAt <= :now)
                    or (message.status = AWAITING_PARENT
                        and (message.stream <> PRICE_LISTS or message.batchCount is not null)
                        and (exists (select lpu.id
                                       from Lpu lpu
                                      where message.awaitedParentKind = CONTRAGENT
                                        and lpu.externalId = message.awaitedParentId)
                             or exists (select contract.id
                                          from LpuContract contract
                                         where message.awaitedParentKind = LPU_CONTRACT
                                           and contract.externalId = message.awaitedParentId)
                             or exists (select service.id
                                          from ContragentService service
                                         where message.awaitedParentKind = CONTRAGENT_SERVICE
                                           and service.externalId = message.awaitedParentId)
                             or exists (select stranger.externalId
                                          from ForeignReference stranger
                                         where stranger.externalId = message.awaitedParentId
                                           and stranger.kind = message.awaitedParentKind))))
               and not exists (select earlier.messageId
                                 from InboundMessage earlier
                                where earlier.stream = message.stream
                                  and earlier.status = RETRY
                                  and earlier.nextRetryAt > :now
                                  and earlier.receivedAt < message.receivedAt)
             order by message.receivedAt
            """)
    List<InboundMessage> findDueForImport(@Param("now") Instant now, Limit limit);

    /**
     * Все сообщения одной многочастной передачи, включая текущее. Нужны сборщику
     * прайсов: применить передачу можно только целиком, а её части приходят
     * в непредсказуемом порядке.
     */
    List<InboundMessage> findByCorrelationId(UUID correlationId);

    /**
     * GUID услуги передачи {@code cid} так, как он записан в теле её части, ещё лежащей в буфере: финального
     * сообщения, если оно пришло, иначе самой ранней. Пусто, если в буфере частей передачи нет. Читается одно поле
     * тела, а не строки целиком.
     */
    @Query(nativeQuery = true, value = """
            select payload -> 'data' ->> 'keyValue'
              from inbound_message
             where correlation_id = :cid
               and status = 'BUFFERED'
             order by batch_count nulls last, received_at
             limit 1
            """)
    Optional<String> findBufferedTransferService(@Param("cid") UUID cid);

    /**
     * GUID услуги каждой части передачи {@code cid}, лежащей в буфере, так, как он записан в её теле; {@code null} —
     * у части, где поля нет или оно равно JSON {@code null}. Читается одно поле тела, сущности в контекст не
     * загружаются: запрос можно делать до блокировки строки услуги, не рискуя потом работать с устаревшими частями.
     */
    @Query(nativeQuery = true, value = """
            select payload -> 'data' ->> 'keyValue'
              from inbound_message
             where correlation_id = :cid
               and status = 'BUFFERED'
            """)
    List<String> findBufferedTransferServices(@Param("cid") UUID cid);

    /** Есть ли у передачи {@code cid} часть в одном из статусов {@code statuses}. Строки сырья не загружаются. */
    boolean existsByCorrelationIdAndStatusIn(UUID correlationId, Set<InboundStatus> statuses);

    /**
     * Передачи в буфере, первая часть которых принята раньше {@code receivedBefore}, самые давние первыми. Передача с
     * потерянной у источника частью не соберётся никогда, и заметить её можно только по давности.
     */
    default List<BufferedTransfer> findTransfersBufferedBefore(Instant receivedBefore, Limit limit) {
        return findTransfersInStatusBefore(InboundStatus.BUFFERED, receivedBefore, limit);
    }

    @Query("""
            select message.correlationId as correlationId,
                   min(message.receivedAt) as firstPartReceivedAt,
                   count(message) as parts
              from InboundMessage message
             where message.status = :status
             group by message.correlationId
            having min(message.receivedAt) < :receivedBefore
             order by min(message.receivedAt)
            """)
    List<BufferedTransfer> findTransfersInStatusBefore(
            @Param("status") InboundStatus status,
            @Param("receivedBefore") Instant receivedBefore,
            Limit limit);

    /**
     * Сколько сообщений каждого потока лежит в каждом незавершённом статусе и когда принято самое давнее, для метрик.
     * Статусы записаны значениями, а не параметрами: так PostgreSQL при любом плане берёт частичный индекс по
     * незавершённым строкам, а не перебирает всю таблицу.
     */
    @Query("""
            select message.stream as stream,
                   message.status as status,
                   count(message) as messages,
                   min(message.receivedAt) as oldestReceivedAt
              from InboundMessage message
             where message.status in (NEW, RETRY, BUFFERED, AWAITING_PARENT, FAILED)
             group by message.stream, message.status
            """)
    List<StatusBacklog> countUnfinishedByStreamAndStatus();

    /**
     * Сколько многочастных передач в буфере ждут своих частей с момента раньше {@code receivedBefore}. Первая часть
     * передачи принята раньше этого момента тогда и только тогда, когда раньше него принята хотя бы одна её часть,
     * поэтому достаточно сосчитать разные передачи среди таких строк. Тела не читаются.
     */
    @Query("""
            select count(distinct message.correlationId)
              from InboundMessage message
             where message.status = BUFFERED
               and message.receivedAt < :receivedBefore
            """)
    long countTransfersBufferedBefore(@Param("receivedBefore") Instant receivedBefore);

    /**
     * Сколько тел, принятых раньше {@code receivedBefore}, ещё ждут стирания по сроку хранения. Отбор тот же, что
     * у {@link #forgetPayloadsReceivedBefore}, слово в слово: так обоим запросам подходит один частичный индекс.
     */
    @Query(nativeQuery = true, value = """
            select count(*)
              from inbound_message
             where payload is not null
               and (stream in ('POLICIES', 'INSURANCE_CONTRACTS') or status = 'SKIPPED'
                    or (status = 'FAILED' and parent_wait_since is not null))
               and received_at < :receivedBefore
            """)
    long countPayloadsToForgetReceivedBefore(@Param("receivedBefore") Instant receivedBefore);

    /**
     * Стирает тела не больше {@code limit} самых старых строк, принятых раньше {@code receivedBefore}; у полиса
     * стирается и причина исхода, где мог остаться текст ошибки со значениями полей. Отбор повторён снаружи подзапроса,
     * чтобы PostgreSQL перепроверил изменённую строку. Транзакцию открывает вызывающий.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update inbound_message
               set payload = null,
                   last_error = case when stream = 'POLICIES' then null else last_error end
             where payload is not null
               and (stream in ('POLICIES', 'INSURANCE_CONTRACTS') or status = 'SKIPPED'
                    or (status = 'FAILED' and parent_wait_since is not null))
               and message_id in (select message_id
                                    from inbound_message
                                   where payload is not null
                                     and (stream in ('POLICIES', 'INSURANCE_CONTRACTS') or status = 'SKIPPED'
                                          or (status = 'FAILED' and parent_wait_since is not null))
                                     and received_at < :receivedBefore
                                   order by received_at
                                   limit :limit)
            """)
    int forgetPayloadsReceivedBefore(@Param("receivedBefore") Instant receivedBefore, @Param("limit") int limit);
}
