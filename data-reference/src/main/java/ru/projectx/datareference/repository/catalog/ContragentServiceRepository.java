package ru.projectx.datareference.repository.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.catalog.ContragentService;

@Repository
public interface ContragentServiceRepository extends JpaRepository<ContragentService, UUID> {

    Optional<ContragentService> findByExternalId(UUID externalId);

    @Query("""
            select service from ContragentService service
             where service.contract.id = :lpuContractId
               and service.archive = false
               and service.externalAppealTypeId in :appealTypeExternalIds
             order by service.name
            """)
    List<ContragentService> findActiveByContractAndAppealTypeIn(
            UUID lpuContractId, Collection<UUID> appealTypeExternalIds);
}
