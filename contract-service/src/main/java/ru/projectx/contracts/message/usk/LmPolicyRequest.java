package ru.projectx.contracts.message.usk;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.UUID;

public record LmPolicyRequest(
        UUID id,
        @JsonUnwrapped UskRequestTerms terms,
        UUID policyholderParticipantId,
        InsuredPerson insured) {
}
