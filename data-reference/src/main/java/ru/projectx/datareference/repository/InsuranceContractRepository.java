package ru.projectx.datareference.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.InsuranceContract;

/**
 * Договоры страхования. Запись обновляется по {@code external_id} — GUID договора в сообщении 1С, а не по нашему
 * первичному ключу: другого устойчивого идентификатора у сообщения источника нет.
 */
@Repository
public interface InsuranceContractRepository extends JpaRepository<InsuranceContract, UUID> {

    Optional<InsuranceContract> findByExternalId(UUID externalId);
}
