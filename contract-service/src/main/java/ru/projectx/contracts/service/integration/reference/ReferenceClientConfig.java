package ru.projectx.contracts.service.integration.reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.projectx.contracts.config.ReferenceProperties;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class ReferenceClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ReferenceClientConfig.class);

    @Bean
    public ReferenceHttpClient referenceHttpClient(ReferenceProperties settings, JsonMapper json) {
        log.info("справочники читаются по адресу {}, таймауты соединения {} и чтения {}, повторов {} с паузой {}",
                settings.url(), settings.connectTimeout(), settings.readTimeout(),
                settings.retry().maxRetries(), settings.retry().delay());
        return new ReferenceHttpClient(
                ReferenceRestClients.builder(settings, json).build(), ReferenceRetries.of(settings.retry()));
    }
}
