package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OrderingDatesTest {

    private static final String ORDERING_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}$";

    @Test
    void moment_isWrittenInTheGivenZoneWithANumericOffset() {
        assertThat(OrderingDates.format(Instant.parse("2026-02-10T07:04:30Z"), ZoneId.of("Europe/Moscow")))
                .isEqualTo("2026-02-10T10:04:30+03:00");
    }

    @Test
    void zeroOffset_isWrittenAsPlusZeroNeverZ() {
        String written = OrderingDates.format(Instant.parse("2026-02-10T07:04:30Z"), ZoneOffset.UTC);

        assertThat(written).isEqualTo("2026-02-10T07:04:30+00:00").matches(ORDERING_PATTERN);
    }

    @Test
    void fractionOfASecond_isDropped() {
        assertThat(OrderingDates.format(Instant.parse("2026-04-09T20:59:59.999Z"), ZoneId.of("Europe/Moscow")))
                .isEqualTo("2026-04-09T23:59:59+03:00")
                .matches(ORDERING_PATTERN);
    }
}
