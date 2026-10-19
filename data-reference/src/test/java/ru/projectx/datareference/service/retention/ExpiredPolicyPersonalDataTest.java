package ru.projectx.datareference.service.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.mapper.stream.PolicyPersonalDataTerm;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.support.ImportSettings;
import ru.projectx.datareference.support.TestApiProperties;

class ExpiredPolicyPersonalDataTest {

    private static final Instant NOW = Instant.parse("2026-03-02T21:30:00Z");
    private static final Instant ARCHIVED_BY = Instant.parse("2026-01-31T21:30:00Z");
    private static final LocalDate ENDED_BEFORE = LocalDate.of(2026, 2, 1);

    private final PolicyRepository repository = mock(PolicyRepository.class);
    private final ExpiredPolicyPersonalData expired = new ExpiredPolicyPersonalData(repository,
            new PolicyPersonalDataTerm(ImportSettings.defaults().build(), TestApiProperties.defaults()),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void erase_passesTheCutoffTheMomentAndTheLimit() {
        when(repository.erasePersonalDataBeyond(ENDED_BEFORE, ARCHIVED_BY, NOW, 500)).thenReturn(7);

        assertThat(expired.erase(500)).isEqualTo(7);
    }

    @Test
    void count_usesTheCutoffOfTheGivenMoment() {
        when(repository.countPersonalDataBeyond(ENDED_BEFORE, ARCHIVED_BY)).thenReturn(3L);

        assertThat(expired.countAt(NOW)).isEqualTo(3L);
    }
}
