package ru.projectx.datareference.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.PriceListFinalization;

@Repository
public interface PriceListFinalizationRepository extends JpaRepository<PriceListFinalization, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select finalization from PriceListFinalization finalization "
            + "where finalization.serviceExternalId = :serviceExternalId")
    Optional<PriceListFinalization> lockByService(@Param("serviceExternalId") UUID serviceExternalId);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into price_list_finalization (service_external_id, final_message_id)
            values (:serviceExternalId, :finalMessageId)
            on conflict (service_external_id) do update set final_message_id = excluded.final_message_id
            """)
    int recordApplied(
            @Param("serviceExternalId") UUID serviceExternalId, @Param("finalMessageId") UUID finalMessageId);
}
