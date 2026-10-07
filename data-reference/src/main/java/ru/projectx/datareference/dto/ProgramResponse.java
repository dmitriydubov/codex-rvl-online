package ru.projectx.datareference.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.entity.ProgramLpu;

/**
 * Программа страхования для {@code GET /internal/v1/programs/{id}} — то, что сервис Договоров
 * берёт из справочника: наименование, {@code external_id} для 1С:УСК и перечень услуг.
 *
 * <p>Перечень услуг — табличная часть {@code service[]} сообщения 1С о страховой программе, у нас
 * {@code program_lpu}: какие учреждения доступны по программе и на каких условиях.
 * Имя {@code services} сохранено от источника, чтобы поле в ответе и поле в сообщении 1С
 * читались как одно.
 *
 * <p>Строки перечня — строки этой же программы, и идут они в том порядке, в каком их передал вызывающий:
 * порядок задаёт запрос строк перечня.
 *
 * <p>{@code is_archive} не запрошен, но отдаётся: договор, выпущенный по программе
 * с {@code deleteMark}, — ошибка, которую потребитель без этого признака не заметит.
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
