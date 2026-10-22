package ru.projectx.contracts.service.lm;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.LmPolicyRequestProperties;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.repository.LmPolicyRepository;

@Service
public class LmPolicyAwaiting {

    private final LmPolicyRepository policies;
    private final LmPolicyAwaitingMetrics metrics;
    private final Duration threshold;
    private final Clock clock;

    public LmPolicyAwaiting(LmPolicyRepository policies, LmPolicyAwaitingMetrics metrics,
            LmPolicyRequestProperties properties, Clock clock) {
        this.policies = policies;
        this.metrics = metrics;
        this.threshold = properties.awaitingThreshold();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void refresh() {
        Instant now = clock.instant();
        Duration oldest = policies.findEarliestUpdateByStatus(LmPolicyStatus.SUBMITTED)
                .map(submittedAt -> Duration.between(submittedAt, now))
                .orElse(Duration.ZERO);
        long overdue = policies.countByStatusAndUpdatedAtBefore(LmPolicyStatus.SUBMITTED, now.minus(threshold));
        metrics.show(oldest, overdue);
    }
}
