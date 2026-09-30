package ru.projectx.datareference.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Insurer;

/**
 * Договор страхования страхователя для {@code GET /internal/v1/insurance-contracts} —
 * «номер, external_id, наименование организации» из ТЗ сервиса Договоров, а с R-08
 * ещё ИНН и КПП страхователя. Читается по нашему {@code id} в пути либо
 * по {@code external_id} параметром.
 *
 * <p>Наименований у организации в 1С два — краткое и полное, — и какое из них ТЗ имеет
 * в виду под «наименованием», не сказано. Отдаются оба. Страхователь может отсутствовать
 * в справочнике (ТЗ-2: им бывает физлицо, которого в {@code insurers} нет) — тогда
 * {@code insurer} пуст, а договор всё равно наш.
 *
 * @param endDate {@code null} — открытая дата источника
 */
public record InsuranceContractResponse(
        UUID id,
        UUID externalId,
        String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean isArchive,
        @Nullable Organization insurer) {

    public static InsuranceContractResponse of(InsuranceContract contract, @Nullable Insurer insurer) {
        return new InsuranceContractResponse(
                contract.id(), contract.externalId(), contract.number(),
                contract.beginDate(), contract.endDate(), contract.isArchive(),
                insurer == null ? null : Organization.of(insurer));
    }

    /** Страхователь — организация из ТЗ-3 с {@code thisIsLPU = false}. */
    public record Organization(
            UUID id,
            UUID externalId,
            String name,
            @Nullable String fullName,
            @Nullable String inn,
            @Nullable String kpp) {

        static Organization of(Insurer insurer) {
            return new Organization(insurer.id(), insurer.externalId(), insurer.name(), insurer.fullName(),
                    insurer.inn(), insurer.kpp());
        }
    }
}
