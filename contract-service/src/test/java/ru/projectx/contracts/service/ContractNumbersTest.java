package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.config.ContractNumberingProperties;
import ru.projectx.contracts.repository.issuance.ContractNumberRepository;

class ContractNumbersTest {

    private final ContractNumberRepository counters = mock();
    private final ContractNumbers numbers =
            new ContractNumbers(counters, new ContractNumberingProperties(ZoneId.of("Europe/Moscow")));

    @Test
    void next_formatsYearAndSixDigitCounter() {
        given(counters.nextNumber(2026)).willReturn(123);

        assertThat(numbers.next(Instant.parse("2026-02-10T07:05:00Z"))).isEqualTo("DKM-2026-000123");
    }

    @Test
    void next_takesYearByMoscowTime() {
        given(counters.nextNumber(2027)).willReturn(1);

        assertThat(numbers.next(Instant.parse("2026-12-31T21:00:00Z"))).isEqualTo("DKM-2027-000001");
    }

    @Test
    void next_keepsOldYearUntilMoscowMidnight() {
        given(counters.nextNumber(2026)).willReturn(7);

        assertThat(numbers.next(Instant.parse("2026-12-31T20:59:59Z"))).isEqualTo("DKM-2026-000007");
    }
}
