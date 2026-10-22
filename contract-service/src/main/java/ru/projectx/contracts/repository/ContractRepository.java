package ru.projectx.contracts.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;

public interface ContractRepository extends JpaRepository<Contract, UUID>, ContractRepositoryLocking {

    List<Contract> findByRequestIndividualRequestIdAndStatusNotIn(UUID individualRequestId,
            Collection<ContractStatus> statuses);

    List<Contract> findByRequestIndividualRequestId(UUID individualRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Contract> findLockedById(UUID id);
}
