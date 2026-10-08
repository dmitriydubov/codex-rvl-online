package ru.projectx.datareference.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.ContragentService;

/**
 * Upsert идёт по {@code external_id} — GUID объекта в 1С, а не по нашему первичному
 * ключу: другого устойчивого идентификатора у сообщения источника нет.
 */
@Repository
public interface ContragentServiceRepository extends JpaRepository<ContragentService, UUID> {

    Optional<ContragentService> findByExternalId(UUID externalId);

    /**
     * Действующие услуги прайсового договора по видам обращения РВЛ, по наименованию. Услуга без вида обращения не
     * показывается (ТЗ на API, вопрос 7).
     */
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
