package ru.projectx.datareference.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Правила чтения сообщений 1С, добавленные к единственному мапперу Spring Boot: свой бин маппера не помог бы, сервисы
 * всё равно получили бы главный маппер Boot. Мост Hibernate к {@code jsonb} этот маппер не видит, потому что
 * Hibernate создаёт мост по имени класса вне контекста Spring.
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    /**
     * Незнакомые поля игнорируются по контракту 1С, которая добавляет поля без предупреждения; правило задано явно,
     * хотя в Jackson 3 это умолчание. Дробь читается в {@link java.math.BigDecimal}: {@code double} теряет последние
     * знаки у сумм длиннее 15 значащих цифр.
     */
    @Bean
    public JsonMapperBuilderCustomizer oneCMessageReading() {
        return builder -> builder
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
}
