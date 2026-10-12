package ru.projectx.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Программа страхования из справочника с перечнем учреждений и условий обслуживания. Поле {@code services} названо
 * как в ответе справочника и в сообщении 1С:УСК.
 */
public record ReferenceProgram(
        UUID id,
        UUID externalId,
        String name,
        @Nullable String typeProgramm,
        boolean isArchive,
        List<Service> services) implements ReferenceRecord {

    /** Строка перечня программы: учреждение и условия обслуживания в нём; вид покрытия — буквой, как в 1С:УСК. */
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
