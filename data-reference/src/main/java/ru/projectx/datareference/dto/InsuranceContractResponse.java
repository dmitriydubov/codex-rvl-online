package ru.projectx.datareference.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Insurer;

/**
 * Договор страхования для сервиса Договоров вместе со страхователем. У страхователя отдаются оба наименования
 * из 1С, краткое и полное, потому что в ТЗ не сказано, какое из них нужно. Страхователем бывает физическое лицо,
 * которого нет в справочнике страхователей, поэтому {@code insurer} может быть пуст.
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

    /** Страхователь: организация из справочника контрагентов 1С, которая не является ЛПУ. */
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
