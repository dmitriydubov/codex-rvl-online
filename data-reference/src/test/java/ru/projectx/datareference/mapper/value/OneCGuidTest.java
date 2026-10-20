package ru.projectx.datareference.mapper.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OneCGuidTest {

    private static final UUID GUID = UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea");

    @Test
    void canonicalGuid_isRead() {
        assertThat(OneCGuid.parse("77ff17b5-e659-4720-b57f-e8a19c00fcea")).contains(GUID);
    }

    @Test
    void uppercaseGuid_isTheSameGuid() {
        assertThat(OneCGuid.parse("77FF17B5-E659-4720-B57F-E8A19C00FCEA")).contains(GUID);
    }

    @Test
    void mixedCaseGuid_isTheSameGuid() {
        assertThat(OneCGuid.parse("77ff17B5-e659-4720-B57f-e8a19c00FCEA")).contains(GUID);
    }

    @Test
    void zeroGuid_isReadAsZeroGuid() {
        assertThat(OneCGuid.parse("00000000-0000-0000-0000-000000000000")).contains(new UUID(0L, 0L));
    }

    @Test
    void zeroGuid_isEmpty() {
        assertThat(OneCGuid.isEmpty(new UUID(0L, 0L))).isTrue();
    }

    @Test
    void realGuid_isNotEmpty() {
        assertThat(OneCGuid.isEmpty(GUID)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1-2-3-4-5",
            "+1-2-3-4-5",
            "0-0-0-0-0",
            "77ff17b5-e659-4720-b57f-e8a19c00fce",
            "077ff17b5-e659-4720-b57f-e8a19c00fce",
            "77ff17b5-e659-4720-b57f-+8a19c00fcea",
            "77ff17b5-e659-4720-b57f-e8a19c00fce\uFF15",
            "77ff17b5-e659-4720-b57f-e8a19c00fce\u0665",
            "77ff17b5-e659-4720-b57f-e8a19c00fce\uFF21"
    })
    void guidThatJdkReadsLeniently_isRejected(String lenient) {
        assertThat(OneCGuid.parse(lenient)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "77ff17b5-e659-4720-b57f-e8a19c00fcea0",
            "77ff17b5-e659-4720-b57f-e8a19c00fceg",
            "{77ff17b5-e659-4720-b57f-e8a19c00fcea}",
            "77ff17b5e6594720b57fe8a19c00fcea",
            "77ff17b5\u2010e659\u20104720\u2010b57f\u2010e8a19c00fcea",
            " 77ff17b5-e659-4720-b57f-e8a19c00fcea",
            "77ff17b5-e659-4720-b57f-e8a19c00fcea\n",
            ""
    })
    void notAGuid_isRejected(String notAGuid) {
        assertThat(OneCGuid.parse(notAGuid)).isEmpty();
    }
}
