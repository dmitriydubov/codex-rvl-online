package ru.projectx.contracts.repository.issuance;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;

public interface IssuanceRequestRepository extends JpaRepository<IssuanceRequest, UUID> {

    boolean existsByIndividualRequestIdAndCancelledAtIsNotNull(UUID individualRequestId);
}
