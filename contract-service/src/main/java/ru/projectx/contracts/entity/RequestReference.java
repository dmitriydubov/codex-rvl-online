package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public record RequestReference(
        @Column(name = "individual_request_id", nullable = false, updatable = false) UUID individualRequestId,
        @Column(name = "insurance_contract_id", nullable = false) UUID insuranceContractId,
        @Column(name = "insurer_policy_id", nullable = false) UUID insurerPolicyId) {
}
