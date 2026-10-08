package ru.projectx.datareference.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.entity.ProgramLpu;

/**
 * Программа страхования для сервиса Договоров с перечнем учреждений и условий; поле {@code services} названо
 * как в сообщении 1С, чтобы читаться с ним как одно. Признак архива отдаётся без запроса: договор по программе,
 * помеченной в 1С на удаление, — ошибка, которую потребитель без него не заметит.
 */
public record ProgramResponse(
        UUID id,
        UUID externalId,
        String name,
        @Nullable String typeProgramm,
        boolean isArchive,
        List<Service> services) {

    /** Ответ по программе и её строкам перечня, уже упорядоченным; чужие строки сюда не передаются. */
    public static ProgramResponse of(Program program, List<ProgramLpu> orderedRows) {
        List<Service> services = orderedRows.stream().map(Service::of).toList();
        return new ProgramResponse(
                program.id(), program.externalId(), program.name(), program.typeProgramm(),
                program.isArchive(), services);
    }

    /** Строка перечня: учреждение и условия обслуживания по нему. */
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
