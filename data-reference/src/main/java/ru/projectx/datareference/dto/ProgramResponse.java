package ru.projectx.datareference.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.catalog.Program;
import ru.projectx.datareference.entity.catalog.ProgramLpu;

public record ProgramResponse(
        UUID id,
        UUID externalId,
        String name,
        @Nullable String typeProgramm,
        boolean isArchive,
        List<Service> services) {

    public static ProgramResponse of(Program program, List<ProgramLpu> orderedRows) {
        List<Service> services = orderedRows.stream().map(Service::of).toList();
        return new ProgramResponse(
                program.id(), program.externalId(), program.name(), program.typeProgramm(),
                program.isArchive(), services);
    }

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
            ProgramLpu.CoverageType coverageType) {

        static Service of(ProgramLpu row) {
            return new Service(
                    row.lpu().id(), row.lpu().externalId(), row.lpu().name(),
                    row.externalAppealTypeId(), row.beginDate(), row.endDate(),
                    row.layerNumber(), row.directAttachment(), row.percentCopayment(),
                    row.coverageType());
        }
    }
}
