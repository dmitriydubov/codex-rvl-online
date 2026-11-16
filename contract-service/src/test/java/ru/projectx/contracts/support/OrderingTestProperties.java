package ru.projectx.contracts.support;

import java.time.Duration;
import java.time.ZoneId;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;

public final class OrderingTestProperties {

    public static final int BATCH = 7;
    public static final Duration PAYMENT_TERM = Duration.ofHours(72);
    public static final Duration MIN_PAYMENT_TERM = Duration.ofHours(1);
    public static final Duration RECONCILIATION_THRESHOLD = Duration.ofMinutes(15);
    public static final Duration PAYMENT_GRACE = Duration.ofHours(1);
    public static final int FIX_REPEATS = 2;

    private OrderingTestProperties() {
    }

    public static OrderingProperties attempts(int attempts) {
        return new OrderingProperties("http://ordering.test", Duration.ofSeconds(1), Duration.ofSeconds(1), "rvl",
                "rvl-lk", "notActual", new OrderingProperties.Retry(attempts, Duration.ZERO),
                new OrderingProperties.Document(ZoneId.of("Europe/Moscow"), Duration.ofSeconds(1)),
                new OrderingProperties.Token("http://keycloak.test/token", "contracts-stand", "secret",
                        Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    public static PaymentOrderPassesProperties passes() {
        return new PaymentOrderPassesProperties(Duration.ofSeconds(10), Duration.ofMinutes(1), BATCH,
                Duration.ofMinutes(1), Duration.ofHours(1), invoices(), reconciliation());
    }

    public static PaymentOrderPassesProperties.Invoices invoices() {
        return new PaymentOrderPassesProperties.Invoices(Duration.ofSeconds(10), PAYMENT_TERM, MIN_PAYMENT_TERM,
                Duration.ofMinutes(30));
    }

    public static PaymentOrderPassesProperties.Reconciliation reconciliation() {
        return new PaymentOrderPassesProperties.Reconciliation(Duration.ofMinutes(1), RECONCILIATION_THRESHOLD,
                PAYMENT_GRACE, FIX_REPEATS, Duration.ofMinutes(1));
    }
}
