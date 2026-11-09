package ru.projectx.contracts.service.integration.ordering;

import java.time.Duration;
import java.time.ZoneId;
import ru.projectx.contracts.config.payment.OrderingProperties;

final class OrderingSettings {

    static final String CLIENT_SECRET = "stand-secret-7f3a";

    private OrderingSettings() {
    }

    static OrderingProperties at(String orderingUrl, String tokenUri) {
        return new OrderingProperties(orderingUrl, Duration.ofSeconds(1), Duration.ofMillis(700), "rvl", "rvl-lk",
                "notActual", new OrderingProperties.Retry(3, Duration.ZERO),
                new OrderingProperties.Document(ZoneId.of("Europe/Moscow"), Duration.ofSeconds(1)),
                new OrderingProperties.Token(tokenUri, "contracts-stand", CLIENT_SECRET, Duration.ofSeconds(1),
                        Duration.ofMillis(700)));
    }
}
