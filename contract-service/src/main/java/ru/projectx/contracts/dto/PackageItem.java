package ru.projectx.contracts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.entity.DocumentKind;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PackageItem(
        UUID participantId,
        DocumentKind documentKind,
        @Nullable UUID contractId,
        @Nullable String contractNumber,
        @Nullable Integer contractVersion,
        DocumentState state,
        @Nullable BigDecimal premiumAmount,
        @Nullable String policyNumber,
        @Nullable PackageAcceptance acceptance,
        @Nullable PackagePayment payment,
        List<PackageDocument> documents,
        @Nullable String errorCode,
        @Nullable String errorMessage) {

    public PackageItem {
        documents = List.copyOf(documents);
    }

    public record PackageAcceptance(Instant acceptedAt, int contractVersion) {
    }

    public record PackagePayment(PaymentState status, BigDecimal amount) {
    }

    public record PackageDocument(UUID documentId, String docType, String kind, int version, @Nullable String url) {
    }

    public enum PaymentState {
        PENDING
    }
}
