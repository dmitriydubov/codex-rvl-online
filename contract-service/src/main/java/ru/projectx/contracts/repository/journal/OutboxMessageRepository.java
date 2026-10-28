package ru.projectx.contracts.repository.journal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.projectx.contracts.entity.outbox.OutboxMessage;
import ru.projectx.contracts.entity.outbox.OutboxStatus;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    default List<OutboxMessage> findDue(Instant now, Limit limit) {
        return findDueIn(OutboxStatus.NEW, now, limit);
    }

    @Query("""
            select m from OutboxMessage m
             where m.status = :status
               and (m.nextAttemptAt is null or m.nextAttemptAt <= :now)
             order by m.createdAt, m.id
            """)
    List<OutboxMessage> findDueIn(@Param("status") OutboxStatus status, @Param("now") Instant now, Limit limit);

    @Modifying
    @Query(value = """
            update outbox_messages
               set payload = null
             where id in (select id from outbox_messages
                           where payload is not null
                             and status in ('PUBLISHED', 'FAILED')
                             and created_at < :cutoff
                           limit :batch)
            """, nativeQuery = true)
    int eraseFinishedPayloadsCreatedBefore(@Param("cutoff") Instant cutoff, @Param("batch") int batch);
}
