package ru.projectx.contracts.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;

public interface LmPolicyRepository extends JpaRepository<LmPolicy, UUID> {

    List<LmPolicy> findByRequestIndividualRequestIdAndStatusNotIn(UUID individualRequestId,
            Collection<LmPolicyStatus> statuses);

    List<LmPolicy> findByRequestIndividualRequestId(UUID individualRequestId);

    List<LmPolicy> findByStatusOrderByCreatedAtAscIdAsc(LmPolicyStatus status, Limit limit);
}
