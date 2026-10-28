package ru.projectx.contracts.service.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PostgresDurationsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0        | PT0S",
            "250      | PT0.25S",
            "1500us   | PT0.0015S",
            "120 ms   | PT0.12S",
            "5s       | PT5S",
            "1min     | PT1M",
            "2h       | PT2H",
            "1d       | PT24H",
            " 30s     | PT30S"})
    void parsed(String value, Duration expected) {
        assertThat(PostgresDurations.parse(value)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "s", "5S", "5 sec", "1.5s", "-1s", "5m", "abc"})
    void unparsed_isEmpty(String value) {
        assertThat(PostgresDurations.parse(value)).isEmpty();
    }
}
