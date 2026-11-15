package ru.projectx.datareference.repository.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.catalog.InsuranceContract;

@Repository
public interface InsuranceContractRepository extends JpaRepository<InsuranceContract, UUID> {

    Optional<InsuranceContract> findByExternalId(UUID externalId);

    List<InsuranceContract> findAllByExternalIdIn(Collection<UUID> externalIds);

    List<InsuranceContract> findAllByExternalInsurerId(UUID externalInsurerId);

    List<InsuranceContractKey> findByArchiveFalseOrderByIdAsc(Limit limit);
}
