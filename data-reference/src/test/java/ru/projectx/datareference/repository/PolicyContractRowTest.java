package ru.projectx.datareference.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Строка полиса для сервиса Договоров — DTO-проекция Spring Data: из базы выбираются ровно свойства,
 * названные её компонентами. Поэтому состав компонентов и есть гарантия, что колонки ПДн (ФИО, дата
 * рождения, пол, почта) не читаются: поле ПДн, добавленное сюда, ответ не изменило бы, а запрос — да.
 */
class PolicyContractRowTest {

    @Test
    void components_areOnlyIdsNumberPeriodAndArchiveFlag() {
        assertThat(Arrays.stream(PolicyContractRow.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("id", "externalId", "number", "beginDate", "endDate", "archive");
    }
}
