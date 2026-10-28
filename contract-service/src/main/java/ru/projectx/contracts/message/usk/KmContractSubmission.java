package ru.projectx.contracts.message.usk;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record KmContractSubmission(
        UUID id,
        String number,
        @JsonUnwrapped UskRequestTerms terms,
        BigDecimal premiumAmount,
        OffsetDateTime paidAt,
        OffsetDateTime acceptedAt,
        KmContractPerson policyholder,
        KmContractPerson insured,
        List<CertificateDocument> documents) {
}
