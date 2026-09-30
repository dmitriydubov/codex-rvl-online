package ru.projectx.datareference.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Чтение JSON приложением.
 *
 * <p>Бин объявлен явно, а не взят у автоконфигурации: она приезжает вместе со стартером
 * JSON, а у сервиса пока нет ни его, ни веб-слоя — только сам Jackson, на котором
 * разбираются сообщения 1С.
 *
 * <p>Мост Hibernate к колонкам {@code jsonb} ({@link Jackson3JsonFormatMapper}) этот бин
 * не использует и использовать не может: его создаёт Hibernate по имени класса,
 * до и вне контекста Spring.
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    /**
     * Незнакомое поле игнорируется — это прямое правило контракта 1С («поля, которых нет
     * в маппинге, игнорируются молча»). 1С добавляет поля, не спрашивая нас, и падать
     * на этом значило бы останавливать обмен из-за расширения источника.
     *
     * <p>Задано явно, хотя в Jackson 3 это и так умолчание: правило контракта должно быть
     * видно в коде, а не зависеть от версии библиотеки.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
