package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Правка справочника прямо в базе мимо 1С:УСК, как ручной SQL дежурного: кэш ответов о ней не узнаёт. Правка обязана
 * изменить ровно одну строку, иначе опечатка в имени или GUID не правила бы ничего и сценарий проходил бы вхолостую.
 */
public class DirectEditSteps {

    private static final Map<String, String> UPDATES = Map.of(
            "наименование санатория", "update lpu set name = :value where external_id = :externalId",
            "цену прайса услуги", """
                    update price_list_items set price = cast(:value as numeric)
                     where contragent_service_id = (select id from contragent_services where external_id = :externalId)
                    """,
            "наименование кода МКБ-10", "update mkb10 set name = :value where external_id = :externalId",
            "номер договора страхования", "update insurance_contracts set number = :value where external_id = :externalId",
            "наименование программы", "update programs set name = :value where external_id = :externalId",
            "номер полиса", "update policies set number = :value where external_id = :externalId");

    private final JdbcClient jdbc;

    public DirectEditSteps(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Дано("дежурный исправил прямо в базе Дата-референса {string} у записи {string} на {string}")
    public void editedDirectly(String what, String externalId, String value) {
        assertThat(UPDATES).as("что умеет править шаг").containsKey(what);

        int updated = jdbc.sql(UPDATES.get(what))
                .param("value", value)
                .param("externalId", UUID.fromString(externalId))
                .update();

        assertThat(updated).as("правка «%s» записи %s", what, externalId).isEqualTo(1);
    }
}
