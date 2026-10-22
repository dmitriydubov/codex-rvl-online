package ru.projectx.contracts.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.projectx.contracts.entity.ContractAcceptance;

public interface ContractAcceptanceRepository extends JpaRepository<ContractAcceptance, UUID> {

    Optional<ContractAcceptance> findByContractId(UUID contractId);

    List<ContractAcceptance> findByContractIdIn(Collection<UUID> contractIds);
}
