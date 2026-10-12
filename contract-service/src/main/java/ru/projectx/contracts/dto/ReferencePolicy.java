package ru.projectx.contracts.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Полис из справочника без персональных данных застрахованного: их сервис Договоров получает от сервиса Заявок.
 * Пустая дата окончания означает бессрочный полис.
 */
public record ReferencePolicy(
        UUID id,
        UUID externalId,
        String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean isArchive) implements ReferenceRecord {
}
