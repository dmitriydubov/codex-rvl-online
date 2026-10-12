package ru.projectx.contracts.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.projectx.contracts.entity.ContractPerson;

public interface ContractPersonRepository extends JpaRepository<ContractPerson, UUID> {

    List<ContractPerson> findByContractIdOrderByRole(UUID contractId);

    List<ContractPerson> findByLmPolicyIdOrderByRole(UUID lmPolicyId);

    void deleteByContractId(UUID contractId);

    void deleteByLmPolicyId(UUID lmPolicyId);
}
