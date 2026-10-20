package ru.projectx.datareference.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarmupRunTest {

    private static final Instant NOW = Instant.parse("2026-10-20T09:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(30);
    private static final String FIRST = "/internal/v1/programs/1";
    private static final String SECOND = "/internal/v1/programs/2";
    private static final String THIRD = "/internal/v1/programs/3";

    @Mock
    private LocalApiClient api;

    private final WarmupRun run = new WarmupRun(Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void everyReadAnswered_completesWithTheNumberOfReads() throws Exception {
        when(api.read(any(), any())).thenReturn(200);

        WarmupOutcome outcome = run.through(api, List.of(FIRST, SECOND, THIRD), DEADLINE);

        assertThat(outcome).isEqualTo(WarmupOutcome.completed(3));
        InOrder order = inOrder(api);
        order.verify(api).read(FIRST, Duration.ofSeconds(30));
        order.verify(api).read(SECOND, Duration.ofSeconds(30));
        order.verify(api).read(THIRD, Duration.ofSeconds(30));
    }

    @Test
    void clientErrorAnswers_areNormalReads() throws Exception {
        when(api.read(any(), any())).thenReturn(404, 409, 499);

        assertThat(run.through(api, List.of(FIRST, SECOND, THIRD), DEADLINE)).isEqualTo(WarmupOutcome.completed(3));
    }

    @Test
    void serviceUnavailable_stopsTheWarmupBeforeTheNextRead() throws Exception {
        when(api.read(FIRST, Duration.ofSeconds(30))).thenReturn(200);
        when(api.read(SECOND, Duration.ofSeconds(30))).thenReturn(503);

        WarmupOutcome outcome = run.through(api, List.of(FIRST, SECOND, THIRD), DEADLINE);

        assertThat(outcome).isEqualTo(WarmupOutcome.interrupted("ответ 503", 1));
        verify(api, never()).read(THIRD, Duration.ofSeconds(30));
    }

    @Test
    void serverError_stopsTheWarmup() throws Exception {
        when(api.read(FIRST, Duration.ofSeconds(30))).thenReturn(500);

        assertThat(run.through(api, List.of(FIRST, SECOND), DEADLINE))
                .isEqualTo(WarmupOutcome.interrupted("ответ 500", 0));
    }

    @Test
    void requestFailure_stopsTheWarmupNamingOnlyTheFailureType() throws Exception {
        when(api.read(FIRST, Duration.ofSeconds(30))).thenThrow(new HttpTimeoutException("request timed out"));

        assertThat(run.through(api, List.of(FIRST, SECOND), DEADLINE))
                .isEqualTo(WarmupOutcome.interrupted("отказ запроса: HttpTimeoutException", 0));
    }

    @Test
    void connectionFailure_stopsTheWarmup() throws Exception {
        when(api.read(FIRST, Duration.ofSeconds(30))).thenThrow(new IOException("connection refused"));

        assertThat(run.through(api, List.of(FIRST, SECOND), DEADLINE))
                .isEqualTo(WarmupOutcome.interrupted("отказ запроса: IOException", 0));
    }

    @Test
    void interruption_stopsTheWarmupAndKeepsTheInterruptFlag() throws Exception {
        when(api.read(FIRST, Duration.ofSeconds(30))).thenThrow(new InterruptedException());

        WarmupOutcome outcome = run.through(api, List.of(FIRST, SECOND), DEADLINE);

        assertThat(outcome).isEqualTo(WarmupOutcome.interrupted("остановка сервиса", 0));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(api, never()).read(SECOND, Duration.ofSeconds(30));
    }

    @Test
    void eachReadWaits_onlyForWhatIsLeftUntilTheDeadline() throws Exception {
        MovingClock clock = new MovingClock(NOW);
        WarmupRun moving = new WarmupRun(clock);
        when(api.read(FIRST, Duration.ofSeconds(30))).thenAnswer(read -> {
            clock.advance(Duration.ofSeconds(20));
            return 200;
        });
        when(api.read(SECOND, Duration.ofSeconds(10))).thenReturn(200);

        assertThat(moving.through(api, List.of(FIRST, SECOND), DEADLINE)).isEqualTo(WarmupOutcome.completed(2));
    }

    @Test
    void deadlineReached_stopsBeforeTheNextRead() throws Exception {
        MovingClock clock = new MovingClock(NOW);
        WarmupRun moving = new WarmupRun(clock);
        when(api.read(FIRST, Duration.ofSeconds(30))).thenAnswer(read -> {
            clock.advance(Duration.ofSeconds(30));
            return 200;
        });

        WarmupOutcome outcome = moving.through(api, List.of(FIRST, SECOND), DEADLINE);

        assertThat(outcome).isEqualTo(WarmupOutcome.interrupted("предел времени", 1));
        verify(api, never()).read(eq(SECOND), any());
    }

    @Test
    void oneMillisecondLeft_stillAllowsTheRead() throws Exception {
        when(api.read(FIRST, Duration.ofMillis(1))).thenReturn(200);

        assertThat(run.through(api, List.of(FIRST), NOW.plusMillis(1))).isEqualTo(WarmupOutcome.completed(1));
    }

    @Test
    void deadlineAlreadyPassed_readsNothing() throws Exception {
        assertThat(run.through(api, List.of(FIRST), NOW)).isEqualTo(WarmupOutcome.interrupted("предел времени", 0));
        verify(api, never()).read(any(), any());
    }

    private static final class MovingClock extends Clock {

        private Instant now;

        MovingClock(Instant now) {
            this.now = now;
        }

        void advance(Duration step) {
            now = now.plus(step);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
