package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

/**
 * Соседям уходит идентификатор текущей обработки, чтобы одну операцию находили в журналах всех сервисов; негодное
 * или отсутствующее значение заменяется новым UUID, а не отправляется как есть.
 */
class RequestIdsTest {

    @AfterEach
    void clearMdc() {
        MDC.remove(RequestIds.MDC_KEY);
    }

    @Test
    void currentRequestId_isTakenFromMdc() {
        MDC.put(RequestIds.MDC_KEY, "req-2026.10_A");

        assertThat(RequestIds.current()).isEqualTo("req-2026.10_A");
    }

    @Test
    void withoutMdc_aFreshUuidIsIssued() {
        String issued = RequestIds.current();

        assertThat(UUID.fromString(issued)).hasToString(issued);
        assertThat(RequestIds.current()).isNotEqualTo(issued);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "с пробелом внутри", "кириллица", "line\nbreak"})
    void unacceptableMdcValue_isReplacedByUuid(String unacceptable) {
        MDC.put(RequestIds.MDC_KEY, unacceptable);

        String issued = RequestIds.current();

        assertThat(issued).isNotEqualTo(unacceptable);
        assertThat(UUID.fromString(issued)).hasToString(issued);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "A-Z.a_z-0.9"})
    void acceptable_allowsLettersDigitsDotUnderscoreDash(String value) {
        assertThat(RequestIds.acceptable(value)).isTrue();
    }

    @Test
    void acceptable_allowsUpTo64SymbolsAndNotMore() {
        assertThat(RequestIds.acceptable("a".repeat(64))).isTrue();
        assertThat(RequestIds.acceptable("a".repeat(65))).isFalse();
    }

    @Test
    void header_isTheContourTraceHeader() {
        assertThat(RequestIds.HEADER).isEqualTo("X-Request-Id");
        assertThat(RequestIds.MDC_KEY).isEqualTo("requestId");
    }
}
