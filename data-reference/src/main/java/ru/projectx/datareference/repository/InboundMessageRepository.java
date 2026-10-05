package ru.projectx.datareference.repository;

import java.time.Instant;
import java.util.List;
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
     * Сообщения, которые пора разбирать: ещё не тронутые и дозревшие до следующей попытки, в порядке поступления.
     * Версий у сообщений 1С нет, в справочнике действует последнее полученное, поэтому сообщения одного потока
     * применяются в том порядке, в каком их прислали.
     *
     * <p>Сообщение, отложенное до повтора, держит более поздние сообщения своего потока: пока его срок не наступил,
     * их здесь нет. Иначе более новое сообщение применилось бы раньше, а повтор старого затёр бы его. Когда срок
     * наступил, оно идёт в пачку раньше них, потому что раньше принято; если и эта попытка не удастся, более поздние
     * сообщения потока удержит сам проход разбора. Удержанные сообщения не занимают места в пачке, и сообщения других
     * потоков разбираются как обычно. Окончательный отказ и части передачи прайса, ждущие остальных частей, поток не
     * держат.
     *
     * <p>{@code SKIPPED} и {@code FAILED} сюда не попадают: они терминальные. Переобработать такую строку можно,
     * переведя её обратно в {@code NEW} обычным SQL, но только помня о порядке: строка встанет в поток по своему
     * старому моменту приёма, и её данные затрут то, что пришло после неё. Переобрабатывать безопасно последнее
     * сообщение по объекту или все сообщения потока начиная с него.
     *
     * <p>Статусы записаны в запросе значениями: так PostgreSQL видит, что подзапросу подходит частичный индекс по
     * незавершённым строкам (поток, статус, момент приёма). Сервис работает одной копией, поэтому строки не
     * блокируются; при нескольких копиях два прохода разобрали бы одну строку дважды и порядок потока нарушился бы.
     */
    @Query("""
            select message
              from InboundMessage message
             where (message.status = NEW
                    or (message.status = RETRY and message.nextRetryAt <= :now))
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
     * Многочастные передачи в буфере, первая часть которых принята раньше {@code receivedBefore}: по строке на
     * передачу, самые давние первыми, не больше {@code limit}. Передача, часть которой потерялась у источника,
     * не соберётся никогда и останется в буфере навсегда, а прайс по её услуге тихо останется вчерашним.
     * Заметить это можно только по давности. Тела сообщений запрос не читает.
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
     * Сколько сообщений каждого потока лежит в каждом незавершённом статусе — ждут разбора ({@code NEW},
     * {@code RETRY}), ждут частей передачи ({@code BUFFERED}) или ушли в отказ ({@code FAILED}) — и когда принято
     * самое давнее из них. Строка на пару «поток, статус»; пар без сообщений в ответе нет. Нужен метрикам: запрос
     * считает, а не выбирает строки, и тела не читает.
     *
     * <p>Статусы записаны в запросе значениями, а не параметрами: так PostgreSQL при любом плане видит, что подходит
     * частичный индекс по незавершённым строкам, и не перебирает всю таблицу сырья, где почти все строки разобраны.
     */
    @Query("""
            select message.stream as stream,
                   message.status as status,
                   count(message) as messages,
                   min(message.receivedAt) as oldestReceivedAt
              from InboundMessage message
             where message.status in (NEW, RETRY, BUFFERED, FAILED)
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
     * Сколько тел полисов, принятых раньше {@code receivedBefore}, ещё не стёрто. Тот же отбор, что у
     * {@link #forgetPolicyPayloads}, и тот же частичный индекс; поток записан значением по той же причине.
     */
    @Query("""
            select count(message)
              from InboundMessage message
             where message.stream = POLICIES
               and message.payload is not null
               and message.receivedAt < :receivedBefore
            """)
    long countPolicyPayloadsReceivedBefore(@Param("receivedBefore") Instant receivedBefore);

    /**
     * Стирает тела не больше {@code limit} строк потока полисов, принятых раньше {@code receivedBefore}, начиная
     * с самых старых, и возвращает, сколько стёрто. Нужен сроку хранения персональных данных: строки остаются,
     * тела не читаются в память. Вместе с телом стирается причина исхода: у полиса она могла содержать текст
     * ошибки базы со значениями полей. Метод не открывает транзакцию: её открывает вызывающий, отдельную на каждую
     * пачку.
     *
     * <p>Запрос написан на SQL PostgreSQL, потому что в языке запросов JPA у подзапроса нет ограничения числа
     * строк. Поток записан в запросе значением, а не параметром: так PostgreSQL при любом плане видит, что
     * подходит частичный индекс по строкам полисов с телом, и не перебирает всю таблицу сырья.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update inbound_message
               set payload = null,
                   last_error = null
             where message_id in (select message_id
                                    from inbound_message
                                   where stream = 'POLICIES'
                                     and payload is not null
                                     and received_at < :receivedBefore
                                   order by received_at
                                   limit :limit)
            """)
    int forgetPolicyPayloads(@Param("receivedBefore") Instant receivedBefore, @Param("limit") int limit);
}
