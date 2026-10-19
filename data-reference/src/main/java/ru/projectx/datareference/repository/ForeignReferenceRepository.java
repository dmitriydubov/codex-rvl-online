package ru.projectx.datareference.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.inbound.ForeignReference;
import ru.projectx.datareference.entity.inbound.ParentKind;

@Repository
public interface ForeignReferenceRepository
        extends org.springframework.data.repository.Repository<ForeignReference, UUID> {

    boolean existsByExternalIdAndKind(UUID externalId, ParentKind kind);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into foreign_reference (external_id, kind, recorded_at)
            values (:externalId, :kind, :recordedAt)
            on conflict (external_id) do nothing
            """)
    int remember(
            @Param("externalId") UUID externalId, @Param("kind") String kind, @Param("recordedAt") Instant recordedAt);
}
