package ru.projectx.datareference.repository;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Полис без персональных данных — проекция запроса, а не сущность: id, {@code external_id}, номер, период
 * действия и признак архива. Spring Data выбирает из базы ровно свойства, названные компонентами этой
 * записи, поэтому имена совпадают со свойствами {@code Policy} ({@code archive}, а не {@code isArchive}),
 * а полей ФИО, даты рождения, пола и почты здесь нет: добавить их — значит начать читать ПДн из базы.
 *
 * @param endDate {@code null} — открытая дата источника
 */
public record PolicyContractRow(
        UUID id,
        UUID externalId,
        String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean archive) {
}
