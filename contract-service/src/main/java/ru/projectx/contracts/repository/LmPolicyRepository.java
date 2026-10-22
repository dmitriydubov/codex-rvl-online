package ru.projectx.contracts.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;

public interface LmPolicyRepository extends JpaRepository<LmPolicy, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LmPolicy> findLockedById(UUID id);

    List<LmPolicy> findByRequestIndividualRequestIdAndStatusNotIn(UUID individualRequestId,
            Collection<LmPolicyStatus> statuses);

    List<LmPolicy> findByRequestIndividualRequestId(UUID individualRequestId);

    List<LmPolicy> findByStatusOrderByCreatedAtAscIdAsc(LmPolicyStatus status, Limit limit);

    @Query("select min(p.updatedAt) from LmPolicy p where p.status = :status")
    Optional<Instant> findEarliestUpdateByStatus(LmPolicyStatus status);

    long countByStatusAndUpdatedAtBefore(LmPolicyStatus status, Instant moment);
}
