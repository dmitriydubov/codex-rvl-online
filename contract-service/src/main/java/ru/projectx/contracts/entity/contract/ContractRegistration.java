package ru.projectx.contracts.entity.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public record ContractRegistration(
        @Column(name = "contract_usk_id") UUID contractUskId,
        @Column(name = "contract_usk_number") String contractUskNumber,
        @Column(name = "issued_policy_id") UUID issuedPolicyId,
        @Column(name = "issued_policy_number") String issuedPolicyNumber) {
}
