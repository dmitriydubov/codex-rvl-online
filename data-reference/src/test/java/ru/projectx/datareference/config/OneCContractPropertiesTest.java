package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OneCContractPropertiesTest {

    @Test
    void filledValues_areReturnedAsGiven() {
        var properties = properties("f3876c53-c7d8-4620-b364-9538164ba5d4", "РВЛ", "ЛичноеСтрахование");

        assertThat(properties.rvlActivity()).contains("f3876c53-c7d8-4620-b364-9538164ba5d4");
        assertThat(properties.rvlContract()).contains("РВЛ");
        assertThat(properties.dmsProducts()).containsExactly("ЛичноеСтрахование");
    }

    @Test
    void blankValues_areNotSet() {
        var properties = properties(" ", "", "  ");

        assertThat(properties.rvlActivity()).isEmpty();
        assertThat(properties.rvlContract()).isEmpty();
        assertThat(properties.dmsProducts()).isEmpty();
    }

    @Test
    void missingValues_areNotSet() {
        var properties = properties(null, null);

        assertThat(properties.rvlActivity()).isEmpty();
        assertThat(properties.rvlContract()).isEmpty();
        assertThat(properties.dmsProducts()).isEmpty();
    }

    @Test
    void blankDmsProducts_areDropped_filledOnesKeepTheirOrder() {
        var properties = properties(null, null, "Личное страхование", " ", "ЛичноеСтрахование");

        assertThat(properties.dmsProducts()).containsExactly("Личное страхование", "ЛичноеСтрахование");
    }

    @Test
    void insurerRole_isReturnedAsGiven() {
        var properties = new OneCContractProperties(null, null, List.of(), "РольСтрахователя", "Адрес", List.of());

        assertThat(properties.insurerRole()).isEqualTo("РольСтрахователя");
    }

    private static OneCContractProperties properties(String rvlActivity, String rvlContract, String... dmsProducts) {
        return new OneCContractProperties(
                rvlActivity, rvlContract, Arrays.asList(dmsProducts), "Страхователь", "Адрес", List.of());
    }
}
