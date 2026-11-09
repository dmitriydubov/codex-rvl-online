package ru.projectx.contracts.service.integration.ordering;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class OrderingClientConfig {

    private static final Logger log = LoggerFactory.getLogger(OrderingClientConfig.class);

    @Bean
    public OrderingHttpClient orderingHttpClient(OrderingProperties settings, JsonMapper json,
            OrderingMetrics metrics, Clock clock) {
        log.info("сервис управления заказами: адрес {}, таймауты соединения {} и чтения {}, попыток за проход {}; "
                        + "сервер токенов {}, клиент {}, таймауты соединения {} и чтения {}",
                settings.url(), settings.connectTimeout(), settings.readTimeout(), settings.retry().attempts(),
                settings.token().uri(), settings.token().clientId(), settings.token().connectTimeout(),
                settings.token().readTimeout());
        return OrderingHttpClients.create(settings, json, metrics, clock);
    }
}
