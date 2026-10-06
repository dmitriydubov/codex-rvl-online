package ru.projectx.datareference.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Чтение JSON приложением.
 *
 * <p>Маппер один — тот, что собирает автоконфигурация Spring Boot: на нём и ответы REST, и разбор сообщений 1С.
 * Здесь к нему добавляются правила чтения сообщений 1С. Отдельный бин маппера тут не помог бы: основной маппер Boot
 * помечен как главный, и сервисы всё равно получили бы его.
 *
 * <p>Мост Hibernate к колонкам {@code jsonb} ({@link Jackson3JsonFormatMapper}) этот маппер
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
     *
     * <p>Дробь читается в {@link java.math.BigDecimal}, а не в {@code double}: так сумма доходит
     * до проверок ровно такой, какой её прислала 1С. Двоичная дробь теряет последние знаки у
     * числа длиннее 15 значащих цифр, и третий знак после запятой у суммы с тринадцатью цифрами
     * до запятой пропал бы незамеченным. Целые числа остаются целыми.
     */
    @Bean
    public JsonMapperBuilderCustomizer oneCMessageReading() {
        return builder -> builder
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
}
