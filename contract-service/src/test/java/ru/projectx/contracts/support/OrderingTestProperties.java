package ru.projectx.contracts.support;

import java.time.Duration;
import java.time.ZoneId;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;

public final class OrderingTestProperties {

    public static final int BATCH = 7;

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
                Duration.ofMinutes(1), Duration.ofHours(1));
    }
}
