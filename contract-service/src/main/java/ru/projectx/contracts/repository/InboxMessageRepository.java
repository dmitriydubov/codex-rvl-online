package ru.projectx.contracts.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.projectx.contracts.entity.inbox.InboxMessage;
import ru.projectx.contracts.entity.inbox.InboxStatus;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, UUID> {

    boolean existsByMessageId(UUID messageId);

    default List<InboxMessage> findDue(Instant now, Limit limit) {
        return findDueIn(InboxStatus.NEW, now, limit);
    }

    @Query("""
            select m from InboxMessage m
             where m.status = :status
               and (m.nextAttemptAt is null or m.nextAttemptAt <= :now)
             order by m.receivedAt
            """)
    List<InboxMessage> findDueIn(@Param("status") InboxStatus status, @Param("now") Instant now, Limit limit);

    default Optional<Integer> maxProcessedVersion(UUID correlationId, Collection<String> eventTypes) {
        return maxVersionIn(correlationId, eventTypes, InboxStatus.PROCESSED);
    }

    @Query("""
            select max(m.entityVersion) from InboxMessage m
             where m.correlationId = :correlationId
               and m.eventType in :eventTypes
               and m.status = :status
            """)
    Optional<Integer> maxVersionIn(@Param("correlationId") UUID correlationId,
            @Param("eventTypes") Collection<String> eventTypes, @Param("status") InboxStatus status);

    @Modifying
    @Query(value = """
            update inbox_messages
               set payload = null
             where id in (select id from inbox_messages
                           where payload is not null and received_at < :cutoff
                           limit :batch)
            """, nativeQuery = true)
    int erasePayloadsReceivedBefore(@Param("cutoff") Instant cutoff, @Param("batch") int batch);
}
