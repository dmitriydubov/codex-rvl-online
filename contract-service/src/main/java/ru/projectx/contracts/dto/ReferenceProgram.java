package ru.projectx.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ReferenceProgram(
        UUID id,
        UUID externalId,
        String name,
        @Nullable String typeProgramm,
        boolean isArchive,
        List<Service> services) implements ReferenceRecord {

    public record Service(
            UUID lpuId,
            UUID lpuExternalId,
            String lpuName,
            @Nullable UUID appealTypeExternalId,
            @Nullable LocalDate beginDate,
            @Nullable LocalDate endDate,
            int layerNumber,
            boolean directAttachment,
            BigDecimal percentCopayment,
            String coverageType) {
    }
}
