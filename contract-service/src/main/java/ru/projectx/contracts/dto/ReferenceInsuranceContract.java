package ru.projectx.contracts.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Договор страхования страхователя из справочника. Страхователь пуст, когда им выступает физическое лицо, которого
 * в справочнике организаций нет. Пустая дата окончания означает бессрочный договор.
 */
public record ReferenceInsuranceContract(
        UUID id,
        UUID externalId,
        String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean isArchive,
        @Nullable Insurer insurer) implements ReferenceRecord {

    /** Страхователь: организация из справочника контрагентов 1С:УСК с кратким и полным наименованием. */
    public record Insurer(
            UUID id,
            UUID externalId,
            String name,
            @Nullable String fullName,
            @Nullable String inn,
            @Nullable String kpp) {
    }
}
