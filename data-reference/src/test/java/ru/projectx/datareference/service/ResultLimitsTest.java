package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.exception.InvalidRequestException;

/** Предел выдачи подсказки: не задан — умолчание, задан — от 1 до максимума, иначе 400 по полю limit. */
class ResultLimitsTest {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    @Test
    void notRequested_isDefault() {
        assertThat(ResultLimits.resolve(null, DEFAULT_LIMIT, MAX_LIMIT)).isEqualTo(DEFAULT_LIMIT);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 49, MAX_LIMIT})
    void requestedWithinBounds_isTaken(int requested) {
        assertThat(ResultLimits.resolve(requested, DEFAULT_LIMIT, MAX_LIMIT)).isEqualTo(requested);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, MAX_LIMIT + 1})
    void requestedOutOfBounds_isValidationErrorOnLimit_namingRule(int requested) {
        assertThatThrownBy(() -> ResultLimits.resolve(requested, DEFAULT_LIMIT, MAX_LIMIT))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("limit");
                    assertThat(invalid.getMessage()).isEqualTo("от 1 до 50");
                });
    }
}
