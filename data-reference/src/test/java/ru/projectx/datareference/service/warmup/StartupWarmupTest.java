package ru.projectx.datareference.service.warmup;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import ru.projectx.datareference.config.WarmupProperties;

@ExtendWith(MockitoExtension.class)
class StartupWarmupTest {

    private static final Instant STARTED = Instant.parse("2026-10-20T09:00:00Z");

    @Mock
    private ReferenceWarmup warmup;
    @Mock
    private WarmupJournal journal;

    @Test
    void warmsUntilTheTimeoutAfterStartAndJournalsTheOutcomeWithItsDuration() {
        SteppingClock clock = new SteppingClock(STARTED, Duration.ofMillis(1500));
        WarmupOutcome outcome = WarmupOutcome.completed(9);
        when(warmup.warm(STARTED.plusSeconds(20))).thenReturn(outcome);

        new StartupWarmup(warmup, journal, new WarmupProperties(Duration.ofSeconds(20), 100), clock)
                .run(new DefaultApplicationArguments());

        verify(journal).record(outcome, Duration.ofMillis(1500));
    }

    private static final class SteppingClock extends Clock {

        private final Duration step;
        private Instant next;

        SteppingClock(Instant first, Duration step) {
            this.next = first;
            this.step = step;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant now = next;
            next = next.plus(step);
            return now;
        }
    }
}
