package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GuidsTest {

    @Test
    void canonicalGuid_isParsedInEitherCase() {
        assertThat(Guids.canonical("61a7982c-a0b4-4bbf-9527-60b40b752803"))
                .contains(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"));
        assertThat(Guids.canonical("61A7982C-A0B4-4BBF-9527-60B40B752803"))
                .contains(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1-2-3-4-5", "61a7982ca0b44bbf952760b40b752803", "", "Тестов",
            "61a7982c-a0b4-4bbf-9527-60b40b75280", "61a7982c-a0b4-4bbf-9527-60b40b752803x"})
    void shortOrMalformedValue_isNotAGuid(String value) {
        assertThat(Guids.canonical(value)).isEmpty();
    }
}
