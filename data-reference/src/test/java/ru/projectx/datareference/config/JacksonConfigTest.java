package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class JacksonConfigTest {

    private final ObjectMapper json = OneCJson.mapper();

    @Test
    void mapperOfTheApplication_readsFractionExactly() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(JacksonConfig.class)
                .run(context -> assertThat(context.getBean(ObjectMapper.class)
                        .readTree("{\"limit\":9999999999999.991}").path("limit").decimalValue())
                        .isEqualTo(new BigDecimal("9999999999999.991")));
    }

    @Test
    void fraction_isReadExactlyWithItsTrailingZeros() {
        JsonNode value = json.readTree("{\"limit\":9999999999999.990}").path("limit");

        assertThat(value.isBigDecimal()).isTrue();
        assertThat(value.decimalValue()).isEqualTo(new BigDecimal("9999999999999.990"));
    }

    @Test
    void integer_staysIntegral() {
        JsonNode value = json.readTree("{\"numberLayer\":3}").path("numberLayer");

        assertThat(value.isIntegralNumber()).isTrue();
        assertThat(value.intValue()).isEqualTo(3);
    }

    @Test
    void integerWrittenAsFraction_isNotIntegral() {
        assertThat(json.readTree("{\"numberLayer\":1.0}").path("numberLayer").isIntegralNumber()).isFalse();
    }

    @Test
    void unknownField_isIgnoredWhenReadIntoRecord() {
        assertThat(json.readValue("{\"name\":\"Терапия\",\"extra\":1}", Named.class)).isEqualTo(new Named("Терапия"));
    }

    record Named(String name) {
    }
}
