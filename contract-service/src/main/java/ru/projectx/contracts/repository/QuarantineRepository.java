package ru.projectx.contracts.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.projectx.contracts.entity.inbox.QuarantinedMessage;

public interface QuarantineRepository extends JpaRepository<QuarantinedMessage, UUID> {

    @Modifying
    @Query(value = """
            update inbox_quarantine
               set body = null
             where message_id in (select message_id from inbox_quarantine
                                   where body is not null
                                     and received_at < :cutoff
                                   limit :batch)
            """, nativeQuery = true)
    int eraseBodiesReceivedBefore(@Param("cutoff") Instant cutoff, @Param("batch") int batch);
}
