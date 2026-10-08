package ru.projectx.datareference.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Строка полиса для сервиса Договоров — проекция Spring Data: из базы выбираются ровно её компоненты. Поэтому состав
 * компонентов гарантирует, что колонки персональных данных (ФИО, дата рождения, пол, почта) не читаются.
 */
class PolicyContractRowTest {

    @Test
    void components_areOnlyIdsNumberPeriodAndArchiveFlag() {
        assertThat(Arrays.stream(PolicyContractRow.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("id", "externalId", "number", "beginDate", "endDate", "archive");
    }
}
