package ru.projectx.datareference.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Сообщения 1С, которые ждут родителя, в таблице {@code inbound_message}: те, что не дождались за срок, и те, что
 * устарели, потому что применилось более новое сообщение той же записи.
 *
 * <p>Статус записан в запросах значением, а не параметром: так PostgreSQL видит частичные индексы по ожидающим
 * сообщениям.
 */
@Repository
public interface ParentWaitRepository extends org.springframework.data.repository.Repository<InboundMessage, UUID> {

    /**
     * Сообщения, которые начали ждать не позже {@code deadline}, а их родитель так и не появился в справочнике и не
     * признан не нашим, в порядке приёма. Сообщение, родитель которого уже есть, сюда не попадает, даже если срок
     * вышел: его применит ближайший разбор.
     */
    @Query("""
            select message
              from InboundMessage message
             where message.status = AWAITING_PARENT
               and message.parentWaitSince <= :deadline
               and not exists (select lpu.id
                                 from Lpu lpu
                                where message.awaitedParentKind = CONTRAGENT
                                  and lpu.externalId = message.awaitedParentId)
               and not exists (select contract.id
                                 from LpuContract contract
                                where message.awaitedParentKind = LPU_CONTRACT
                                  and contract.externalId = message.awaitedParentId)
               and not exists (select service.id
                                 from ContragentService service
                                where message.awaitedParentKind = CONTRAGENT_SERVICE
                                  and service.externalId = message.awaitedParentId)
               and not exists (select stranger.externalId
                                 from ForeignReference stranger
                                where stranger.externalId = message.awaitedParentId
                                  and stranger.kind = message.awaitedParentKind)
             order by message.receivedAt
            """)
    List<InboundMessage> findExpiredUnresolved(@Param("deadline") Instant deadline, Limit limit);

    /** Сообщения потока {@code stream} о записи {@code record}, принятые раньше {@code receivedBefore} и ждущие родителя. */
    @Query("""
            select message
              from InboundMessage message
             where message.status = AWAITING_PARENT
               and message.stream = :stream
               and message.recordExternalId = :record
               and message.receivedAt < :receivedBefore
            """)
    List<InboundMessage> findAwaitingOfRecordBefore(
            @Param("stream") OneCStream stream,
            @Param("record") UUID record,
            @Param("receivedBefore") Instant receivedBefore);
}
