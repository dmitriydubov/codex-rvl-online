package ru.projectx.datareference.repository;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Полис без персональных данных (ПДн): проекция, а не сущность. Spring Data выбирает из базы ровно свойства, названные
 * компонентами записи, поэтому имена совпадают со свойствами {@code Policy}, а полей ФИО, даты рождения и почты нет:
 * добавить их значит начать читать ПДн.
 */
public record PolicyContractRow(
        UUID id,
        UUID externalId,
        String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean archive) {
}
