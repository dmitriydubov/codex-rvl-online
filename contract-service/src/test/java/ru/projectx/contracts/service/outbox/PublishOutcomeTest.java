package ru.projectx.contracts.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PublishOutcomeTest {

    @Test
    void onlyASilentOrUnreachableBrokerOrAnInterruptedWait_endsThePass() {
        assertThat(new PublishOutcome.Delivered().endsPass()).isFalse();
        assertThat(new PublishOutcome.Refused("возврат").endsPass()).isFalse();
        assertThat(new PublishOutcome.Rejected("nack").endsPass()).isFalse();
        assertThat(new PublishOutcome.Unconfirmed(Duration.ofSeconds(10)).endsPass()).isTrue();
        assertThat(new PublishOutcome.NotSent("недоступен").endsPass()).isTrue();
        assertThat(new PublishOutcome.Interrupted().endsPass()).isTrue();
    }

    @Test
    void onlyAMessageTheBrokerReceived_countsAsAnAttempt() {
        assertThat(new PublishOutcome.Delivered()).isInstanceOf(PublishOutcome.Attempted.class);
        assertThat(new PublishOutcome.Refused("возврат")).isInstanceOf(PublishOutcome.Attempted.class);
        assertThat(new PublishOutcome.Rejected("nack")).isInstanceOf(PublishOutcome.Attempted.class);
        assertThat(new PublishOutcome.Unconfirmed(Duration.ofSeconds(10))).isInstanceOf(PublishOutcome.Attempted.class);
        assertThat(new PublishOutcome.NotSent("недоступен")).isNotInstanceOf(PublishOutcome.Attempted.class);
        assertThat(new PublishOutcome.Interrupted()).isNotInstanceOf(PublishOutcome.Attempted.class);
    }

    @Test
    void reasons_nameWhatHappened() {
        assertThat(new PublishOutcome.Delivered().reason()).isEqualTo("доставлено");
        assertThat(new PublishOutcome.Unconfirmed(Duration.ofSeconds(10)).reason())
                .isEqualTo("подтверждение брокера не получено за PT10S");
        assertThat(new PublishOutcome.NotSent("брокер недоступен").reason()).isEqualTo("брокер недоступен");
        assertThat(new PublishOutcome.Interrupted().reason())
                .isEqualTo("ожидание подтверждения брокера прервано остановкой сервиса");
    }
}
