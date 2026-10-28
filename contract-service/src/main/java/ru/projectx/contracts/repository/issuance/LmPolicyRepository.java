package ru.projectx.contracts.repository.issuance;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.repository.IssuanceRepositoryLocking;
import ru.projectx.contracts.repository.UskRecordRepository;

public interface LmPolicyRepository extends JpaRepository<LmPolicy, UUID>, IssuanceRepositoryLocking<LmPolicy>,
        UskRecordRepository<LmPolicy, LmPolicyStatus> {

    List<LmPolicy> findByRequestIndividualRequestIdAndStatusNotIn(UUID individualRequestId,
            Collection<LmPolicyStatus> statuses);

    List<LmPolicy> findByRequestIndividualRequestId(UUID individualRequestId);
}
