package ru.projectx.datareference.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.InsuranceContract;

/**
 * Upsert идёт по {@code external_id} — {@code contractId} сообщения ТЗ-2, а не по нашему
 * первичному ключу: другого устойчивого идентификатора у сообщения источника нет.
 */
@Repository
public interface InsuranceContractRepository extends JpaRepository<InsuranceContract, UUID> {

    Optional<InsuranceContract> findByExternalId(UUID externalId);

    /**
     * Договор как граница выдачи для R-01 и R-04 — вход базовой логики A: по договору
     * в архиве действующих полисов нет по определению, и вызывающему отвечается
     * «не найден», как предписывает ТЗ на API.
     */
    Optional<InsuranceContract> findByExternalIdAndArchiveFalse(UUID externalId);
}
