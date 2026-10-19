package ru.projectx.datareference.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.InsuranceContract;

@Repository
public interface InsuranceContractRepository extends JpaRepository<InsuranceContract, UUID> {

    Optional<InsuranceContract> findByExternalId(UUID externalId);

    List<InsuranceContract> findAllByExternalInsurerId(UUID externalInsurerId);
}
