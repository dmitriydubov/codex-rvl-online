package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Защищает то, как настройки обмена с 1С:УСК отличают «значение не задано» от заданного. Пустая строка
 * в переменной окружения должна значить то же, что отсутствие настройки, иначе маппер принял бы её
 * за значение и применял правило с пустым признаком.
 */
class OneCContractPropertiesTest {

    @Test
    void filledValues_areReturnedAsGiven() {
        var properties = properties("f3876c53-c7d8-4620-b364-9538164ba5d4", "РВЛ", "Личное страхование");

        assertThat(properties.rvlActivity()).contains("f3876c53-c7d8-4620-b364-9538164ba5d4");
        assertThat(properties.rvlContract()).contains("РВЛ");
        assertThat(properties.dmsProduct()).contains("Личное страхование");
    }

    @Test
    void blankValues_areNotSet() {
        var properties = properties(" ", "", "  ");

        assertThat(properties.rvlActivity()).isEmpty();
        assertThat(properties.rvlContract()).isEmpty();
        assertThat(properties.dmsProduct()).isEmpty();
    }

    @Test
    void missingValues_areNotSet() {
        var properties = properties(null, null, null);

        assertThat(properties.rvlActivity()).isEmpty();
        assertThat(properties.rvlContract()).isEmpty();
        assertThat(properties.dmsProduct()).isEmpty();
    }

    private static OneCContractProperties properties(String rvlActivity, String rvlContract, String dmsProduct) {
        return new OneCContractProperties(rvlActivity, rvlContract, dmsProduct, "Адрес", List.of());
    }
}
