package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.support.OrderingTestProperties;

class PaymentTermsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final Duration TERM = OrderingTestProperties.PAYMENT_TERM;
    private static final Duration MINIMUM = OrderingTestProperties.MIN_PAYMENT_TERM;

    private final PaymentTerms terms = new PaymentTerms(OrderingTestProperties.passes());

    @Test
    void deadline_isThePaymentTermFromNowWhileTheBoundaryIsFarther() {
        assertThat(terms.deadline(NOW, NOW.plus(TERM).plusNanos(1))).contains(Instant.parse("2026-02-13T07:05:00Z"));
    }

    @Test
    void deadline_isTheBoundaryWhenItComesBeforeThePaymentTermEnds() {
        Instant boundary = NOW.plus(TERM).minusNanos(1);

        assertThat(terms.deadline(NOW, boundary)).contains(boundary);
    }

    @Test
    void deadline_ofTermEndingExactlyOnTheBoundary_isThatMoment() {
        assertThat(terms.deadline(NOW, NOW.plus(TERM))).contains(NOW.plus(TERM));
    }

    @Test
    void deadline_isTheBoundaryWhenExactlyTheMinimalTermIsLeft() {
        Instant boundary = NOW.plus(MINIMUM);

        assertThat(terms.deadline(NOW, boundary)).contains(boundary);
    }

    @Test
    void deadline_isUnknownWhenLessThanTheMinimalTermIsLeft() {
        assertThat(terms.deadline(NOW, NOW.plus(MINIMUM).minusNanos(1))).isEmpty();
    }

    @Test
    void deadline_isUnknownWhenTheBoundaryHasPassed() {
        assertThat(terms.deadline(NOW, NOW.minusSeconds(1))).isEmpty();
    }

    @Test
    void boundary_isPassedOnlyStrictlyAfterIt() {
        assertThat(terms.boundaryPassed(NOW.plusNanos(1), NOW)).isTrue();
        assertThat(terms.boundaryPassed(NOW, NOW)).isFalse();
        assertThat(terms.boundaryPassed(NOW.minusNanos(1), NOW)).isFalse();
    }
}
