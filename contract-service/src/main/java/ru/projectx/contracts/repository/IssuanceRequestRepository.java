package ru.projectx.contracts.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;

public interface IssuanceRequestRepository extends JpaRepository<IssuanceRequest, UUID> {
}
