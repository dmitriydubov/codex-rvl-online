package ru.projectx.contracts.service.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.projectx.contracts.config.ReferenceProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Клиент справочников как бин: один на сервис, с настройками {@code projectx.reference}. Разбор JSON — общий
 * {@link JsonMapper} приложения, чтобы имена полей читались так же, как во всех контрактах контура.
 */
@Configuration(proxyBeanMethods = false)
public class ReferenceClientConfig {

    @Bean
    public ReferenceHttpClient referenceHttpClient(ReferenceProperties settings, JsonMapper json) {
        return new ReferenceHttpClient(
                ReferenceRestClients.builder(settings, json).build(), ReferenceRetries.of(settings.retry()));
    }
}
