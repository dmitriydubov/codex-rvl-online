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
     * Сообщения, которые пора разбирать: ещё не тронутые и дозревшие до следующей
     * попытки. В порядке поступления — сообщения по одному объекту 1С должны применяться
     * в том порядке, в каком их прислали, иначе старое затрёт новое.
     *
     * <p>{@code SKIPPED} и {@code FAILED} сюда не попадают: они терминальные.
     * Переобработать такую строку можно, переведя её обратно в {@code NEW} обычным
     * SQL — например, когда починили маппер или когда наконец приехал родитель,
     * из-за отсутствия которого сообщение было пропущено.
     */
    default List<InboundMessage> findDueForImport(Instant now, Limit limit) {
        return findByStatus(InboundStatus.NEW, InboundStatus.RETRY, now, limit);
    }

    @Query("""
            select message
              from InboundMessage message
             where message.status = :pending
                or (message.status = :retry and message.nextRetryAt <= :now)
             order by message.receivedAt
            """)
    List<InboundMessage> findByStatus(
            @Param("pending") InboundStatus pending,
            @Param("retry") InboundStatus retry,
            @Param("now") Instant now,
            Limit limit);

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
     * Стирает тела не больше {@code limit} строк потока полисов, принятых раньше {@code receivedBefore}, начиная
     * с самых старых, и возвращает, сколько стёрто. Нужен сроку хранения персональных данных: строки остаются,
     * тела не читаются в память. Метод не открывает транзакцию: её открывает вызывающий, отдельную на каждую
     * пачку.
     *
     * <p>Запрос написан на SQL PostgreSQL, потому что в языке запросов JPA у подзапроса нет ограничения числа
     * строк. Поток записан в запросе значением, а не параметром: так PostgreSQL при любом плане видит, что
     * подходит частичный индекс по строкам полисов с телом, и не перебирает всю таблицу сырья.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update inbound_message
               set payload = null
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
