package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Защищает то, что идентификатор из запроса REST читается по тому же правилу записи GUID, что и сообщения 1С:УСК:
 * каноническая запись читается, укороченная не становится чужим GUID.
 */
class CanonicalGuidsTest {

    @Test
    void canonicalRecord_isRead() {
        assertThat(CanonicalGuids.parse("77ff17b5-e659-4720-b57f-e8a19c00fcea"))
                .contains(UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea"));
    }

    @Test
    void shortenedRecord_isNotAGuid() {
        assertThat(CanonicalGuids.parse("1-2-3-4-5")).isEmpty();
    }
}
