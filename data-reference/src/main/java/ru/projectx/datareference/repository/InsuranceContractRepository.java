package ru.projectx.datareference.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.InsuranceContract;

@Repository
public interface InsuranceContractRepository extends JpaRepository<InsuranceContract, UUID> {

    Optional<InsuranceContract> findByExternalId(UUID externalId);
}
