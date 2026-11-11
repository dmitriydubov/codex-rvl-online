package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InboxWorkTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");

    @Test
    void plainWork_isAppliedOnceAtTheGivenMomentAndNeverSkips() {
        List<Instant> applied = new ArrayList<>();
        InboxWork work = applied::add;

        assertThat(work.applyOrSkip(NOW)).isEmpty();
        assertThat(applied).containsExactly(NOW);
    }

    @Test
    void decidingWork_answersItsOwnDecision() {
        DecidingInboxWork skipping = now -> Optional.of("событие по чужому заказу");
        DecidingInboxWork applying = now -> Optional.empty();

        assertThat(skipping.applyOrSkip(NOW)).contains("событие по чужому заказу");
        assertThat(applying.applyOrSkip(NOW)).isEmpty();
    }

    @Test
    void decidingWorkAppliedAsPlainWork_stillMakesItsDecisionOnceAtTheGivenMoment() {
        List<Instant> decided = new ArrayList<>();
        DecidingInboxWork work = now -> {
            decided.add(now);
            return Optional.of("повтор события");
        };

        work.apply(NOW);

        assertThat(decided).containsExactly(NOW);
    }
}
