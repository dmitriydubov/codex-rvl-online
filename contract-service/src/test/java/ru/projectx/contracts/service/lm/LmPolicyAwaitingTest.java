package ru.projectx.contracts.service.lm;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.config.LmPolicyRequestProperties;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class LmPolicyAwaitingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T08:35:00Z");
    private static final Duration THRESHOLD = Duration.ofHours(1);

    private final LmPolicyRepository policies = mock();
    private final LmPolicyAwaitingMetrics metrics = mock();
    private final LmPolicyAwaiting awaiting = new LmPolicyAwaiting(policies, metrics,
            new LmPolicyRequestProperties(Duration.ofSeconds(10), 100, Duration.ofMinutes(1), THRESHOLD),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void submittedPolicies_showTheWaitOfTheOldestAndTheCountSubmittedBeforeTheThreshold() {
        LmPolicy oldest = Issuances.policy("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39", Tasks.CHILD);
        oldest.submit(NOW.minus(Duration.ofMinutes(90)));
        given(policies.findFirstByStatusOrderByUpdatedAtAscIdAsc(LmPolicyStatus.SUBMITTED)).willReturn(Optional.of(oldest));
        given(policies.countByStatusAndUpdatedAtBefore(LmPolicyStatus.SUBMITTED, NOW.minus(THRESHOLD))).willReturn(2L);

        awaiting.refresh();

        verify(metrics).show(Duration.ofMinutes(90), 2);
    }

    @Test
    void noSubmittedPolicies_showZeroWaitAndNothingOverdue() {
        given(policies.findFirstByStatusOrderByUpdatedAtAscIdAsc(LmPolicyStatus.SUBMITTED)).willReturn(Optional.empty());
        given(policies.countByStatusAndUpdatedAtBefore(LmPolicyStatus.SUBMITTED, NOW.minus(THRESHOLD))).willReturn(0L);

        awaiting.refresh();

        verify(metrics).show(Duration.ZERO, 0);
    }
}
