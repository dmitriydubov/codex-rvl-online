package ru.projectx.datareference.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Полис для сервиса Договоров — ровно то, что он берёт по полису (ТЗ Договоров, «Чтение справочников
 * Дата-референса»): номер, {@code external_id} и период действия, плюс наш {@code id} и признак архива,
 * как в каждом ответе сервиса. Персональных данных застрахованного здесь нет и быть не должно (Р20 (а)):
 * их сервис Договоров получает от сервиса Заявок, а поле «на всякий случай» — это утечка.
 *
 * <p>Запись заполняет Spring Data прямо из запроса (динамическая проекция), выбирая свойства полиса
 * по именам компонентов, поэтому признак архива называется как свойство сущности — {@code archive};
 * имя поля в JSON задано явно, как у остальных ответов, — {@code is_archive}.
 *
 * @param endDate {@code null} — открытая дата источника
 */
public record PolicyContractView(
        UUID id,
        UUID externalId,
        String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        @JsonProperty("is_archive") boolean archive) {
}
