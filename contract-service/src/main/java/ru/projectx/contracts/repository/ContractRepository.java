package ru.projectx.contracts.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;

public interface ContractRepository extends JpaRepository<Contract, UUID>, IssuanceRepositoryLocking<Contract>,
        UskRecordRepository<Contract, ContractStatus> {

    List<Contract> findByRequestIndividualRequestIdAndStatusNotIn(UUID individualRequestId,
            Collection<ContractStatus> statuses);

    List<Contract> findByRequestIndividualRequestIdAndStatusAndPaidAtIsNotNull(UUID individualRequestId,
            ContractStatus status);

    List<Contract> findByRequestIndividualRequestId(UUID individualRequestId);
}
