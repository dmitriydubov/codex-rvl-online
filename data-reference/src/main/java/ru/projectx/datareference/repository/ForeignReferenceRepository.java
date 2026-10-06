package ru.projectx.datareference.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.ForeignReference;
import ru.projectx.datareference.entity.ParentKind;

/**
 * Записи 1С:УСК, признанные не нашими, таблица {@code foreign_reference}. Наружу только проверка и запись: читать и
 * удалять строки разбору не нужно.
 */
@Repository
public interface ForeignReferenceRepository
        extends org.springframework.data.repository.Repository<ForeignReference, UUID> {

    /** Признана ли не нашей запись этого вида с этим GUID 1С. */
    boolean existsByExternalIdAndKind(UUID externalId, ParentKind kind);

    /**
     * Запоминает запись как не нашу одним оператором. Повтор ничего не меняет: запись остаётся с моментом, когда её
     * запомнили впервые.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into foreign_reference (external_id, kind, recorded_at)
            values (:externalId, :kind, :recordedAt)
            on conflict (external_id) do nothing
            """)
    int remember(
            @Param("externalId") UUID externalId, @Param("kind") String kind, @Param("recordedAt") Instant recordedAt);
}
