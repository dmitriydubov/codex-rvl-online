package ru.projectx.datareference.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.QuarantinedMessage;

@Repository
public interface QuarantineRepository extends JpaRepository<QuarantinedMessage, UUID> {

    @Modifying
    @Query(nativeQuery = true, value = """
            update quarantine
               set body = null
             where message_id in (select message_id
                                    from quarantine
                                   where body is not null
                                     and received_at < :receivedBefore
                                   order by received_at
                                   limit :limit)
            """)
    int forgetBodies(@Param("receivedBefore") Instant receivedBefore, @Param("limit") int limit);

    @Query("""
            select count(quarantined)
              from QuarantinedMessage quarantined
             where quarantined.body is not null
               and quarantined.receivedAt < :receivedBefore
            """)
    long countBodiesReceivedBefore(@Param("receivedBefore") Instant receivedBefore);
}
