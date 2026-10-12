package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LikePatternsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "иванов     | иванов",
            "50%        | 50!%",
            "a_b        | a!_b",
            "a!b        | a!!b",
            "!%_        | !!!%!_"})
    void escaped_marksPatternCharactersAsLiteral(String value, String expected) {
        assertThat(LikePatterns.escaped(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"ив | ив%", "a_ | a!_%"})
    void prefix_matchesAnythingAfterEscapedValue(String value, String expected) {
        assertThat(LikePatterns.prefix(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"12 | %12%", "1% | %1!%%"})
    void substring_matchesAnythingAroundEscapedValue(String value, String expected) {
        assertThat(LikePatterns.substring(value)).isEqualTo(expected);
    }
}
