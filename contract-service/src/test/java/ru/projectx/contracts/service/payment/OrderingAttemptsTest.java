package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.service.leadership.LeadershipLostException;
import ru.projectx.contracts.support.OrderingTestProperties;

class OrderingAttemptsTest {

    private final OrderingAttempts attempts = new OrderingAttempts(OrderingTestProperties.attempts(3));
    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void success_isReturnedAfterTemporaryFailures() {
        String result = attempts.run(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new OrderingUnavailableException(OrderingOperation.CREATE_ORDER, "HTTP 503");
            }
            return "o-1";
        });

        assertThat(result).isEqualTo("o-1");
        assertThat(calls).hasValue(3);
    }

    @Test
    void temporaryFailure_isTriedTheConfiguredNumberOfTimesThenThrown() {
        assertThatThrownBy(() -> attempts.run(() -> {
            calls.incrementAndGet();
            throw new OrderingUnavailableException(OrderingOperation.CREATE_ORDER, "HTTP 503");
        })).isInstanceOf(OrderingUnavailableException.class);

        assertThat(calls).hasValue(3);
    }

    @Test
    void singleAttempt_isNotRepeated() {
        OrderingAttempts once = new OrderingAttempts(OrderingTestProperties.attempts(1));

        assertThatThrownBy(() -> once.run(() -> {
            calls.incrementAndGet();
            throw new OrderingUnavailableException(OrderingOperation.TOKEN, "HTTP 503");
        })).isInstanceOf(OrderingUnavailableException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void rejection_isNotRepeated() {
        assertThatThrownBy(() -> attempts.run(() -> {
            calls.incrementAndGet();
            throw new OrderingRejectedException(OrderingOperation.CREATE_ORDER, "C");
        })).isInstanceOf(OrderingRejectedException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void lostLeadership_isNotRepeated() {
        assertThatThrownBy(() -> attempts.run(() -> {
            calls.incrementAndGet();
            throw new LeadershipLostException("stand");
        })).isInstanceOf(LeadershipLostException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void error_isRethrownAsIs() {
        assertThatThrownBy(() -> attempts.run(() -> {
            throw new AssertionError("стенд");
        })).isInstanceOf(AssertionError.class);
    }
}
