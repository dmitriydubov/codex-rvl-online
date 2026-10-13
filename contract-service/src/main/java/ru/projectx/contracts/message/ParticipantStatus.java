package ru.projectx.contracts.message;

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
        @Nullable UUID policyId,
        @Nullable String policyNumber,
        PaymentStatus paymentStatus,
        List<StatusDocument> documents,
        @Nullable String errorCode,
        @Nullable String errorMessage) {

    public static ParticipantStatus nothingIssued(UUID id) {
        return new ParticipantStatus(id, IssuedDocumentKind.NONE, null, KmContractState.NOT_REQUIRED, null, null, null,
                PaymentStatus.NOT_REQUIRED, List.of(), null, null);
    }
}
