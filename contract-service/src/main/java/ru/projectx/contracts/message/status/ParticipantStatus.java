package ru.projectx.contracts.message.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParticipantStatus(
        UUID id,
        IssuedDocumentKind documentKind,
        @Nullable UUID kmContractId,
        KmContractState kmContractState,
        @Nullable String kmContractNumber,
        @Nullable UUID kmPolicyId,
        @Nullable String kmPolicyNumber,
        @Nullable UUID policyId,
        @Nullable String policyNumber,
        PaymentStatus paymentStatus,
        @Nullable String paymentUrl,
        List<StatusDocument> documents,
        @Nullable String errorCode,
        @Nullable String errorMessage) {

    public static ParticipantStatus nothingIssued(UUID id) {
        return new ParticipantStatus(id, IssuedDocumentKind.NONE, null, KmContractState.NOT_REQUIRED, null, null, null,
                null, null, PaymentStatus.NOT_REQUIRED, null, List.of(), null, null);
    }
}
