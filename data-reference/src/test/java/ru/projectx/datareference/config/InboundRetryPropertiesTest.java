package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class InboundRetryPropertiesTest {

    private final InboundRetryProperties properties = new InboundRetryProperties(
            List.of(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(30)));

    @Test
    void everyStepOfLadder_hasItsOwnDelay() {
        assertThat(properties.delayBeforeAttempt(1)).contains(Duration.ofSeconds(30));
        assertThat(properties.delayBeforeAttempt(2)).contains(Duration.ofMinutes(5));
        assertThat(properties.delayBeforeAttempt(3)).contains(Duration.ofMinutes(30));
    }

    @Test
    void attemptBeyondLadder_hasNoDelay() {
        assertThat(properties.delayBeforeAttempt(4)).isEmpty();
    }

    @Test
    void attemptBelowFirst_hasNoDelay() {
        assertThat(properties.delayBeforeAttempt(0)).isEmpty();
    }
}
