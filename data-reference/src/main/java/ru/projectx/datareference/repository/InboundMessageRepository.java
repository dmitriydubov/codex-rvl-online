package ru.projectx.datareference.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;

/** Сырьё из 1С. */
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
     * Передачи, зависшие в ожидании своих частей. Передача, часть которой потерялась
     * у источника, не соберётся никогда и останется в буфере навсегда — а прайс
     * по её услуге тихо останется вчерашним. Единственный способ это заметить —
     * следить за давностью.
     */
    List<InboundMessage> findByStatusAndReceivedAtLessThan(InboundStatus status, Instant threshold);

    /**
     * Строки потока, у которых ещё лежит тело, принятые раньше порога. Нужны сроку
     * хранения персональных данных — см. {@link InboundMessage#forgetPayload()}.
     */
    List<InboundMessage> findByStreamAndPayloadIsNotNullAndReceivedAtLessThan(
            OneCStream stream, Instant threshold, Limit limit);
}
