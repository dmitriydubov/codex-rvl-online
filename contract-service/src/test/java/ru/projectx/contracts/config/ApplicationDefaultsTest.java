package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Умолчания {@code application.yml}, на которые опирается развёртывание. Они проверяются в самом файле, без контекста:
 * тесты с базой адрес подменяют, а переменные окружения машины не должны влиять на результат.
 */
class ApplicationDefaultsTest {

    private static PropertySource<?> application;

    @BeforeAll
    static void loadApplicationYaml() throws IOException {
        application = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml")).getFirst();
    }

    /** Свойства пула до миграций не доходят: без параметра в адресе их ошибка несла бы значения строки. */
    @Test
    void defaultDatabaseUrl_hidesServerErrorDetail() {
        assertThat(application.getProperty("spring.datasource.url")).asString()
                .startsWith("${DB_URL:jdbc:postgresql://")
                .endsWith("?logServerErrorDetail=false}");
    }

    /** Порт 8081 открывает образ, и на него настроены мониторинг и пробы актуатора. */
    @Test
    void defaultManagementPort_is8081() {
        assertThat(application.getProperty("management.server.port")).asString().isEqualTo("${MANAGEMENT_PORT:8081}");
    }

    /** Подключение из spring.rabbitmq.* не строится: с двумя своими фабриками автонастройка не поднялась бы. */
    @Test
    void rabbitAutoConfiguration_isExcluded() {
        assertThat(application.getProperty("spring.autoconfigure.exclude[0]")).asString()
                .isEqualTo("org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration");
        assertThat(application.getProperty("management.health.rabbit.enabled")).asString().isEqualTo("false");
    }

    /**
     * Известный пароль из репозитория подошёл бы к любому брокеру с таким же. Умолчание пустое, а не отсутствует:
     * неразрешённый плейсхолдер Boot оставляет строкой, и проверка настроек приняла бы её за пароль.
     */
    @ParameterizedTest
    @ValueSource(strings = {"internal", "onec"})
    void brokerPassword_hasEmptyDefault(String broker) {
        assertThat(application.getProperty("projectx.brokers." + broker + ".connection.password")).asString()
                .isEqualTo("${RABBITMQ_" + broker.toUpperCase(Locale.ROOT) + "_PASSWORD:}");
    }

    /** Имена очередей — из документа контура «Точки интеграций»; менять их можно только вместе со списком брокера. */
    @Test
    void queueNames_matchTheContourContract() {
        assertThat(queueDefault("internal", "task-changes")).isEqualTo("rvl_requests_to_rvl_contracts_task-changes");
        assertThat(queueDefault("internal", "status-changes"))
                .isEqualTo("rvl_contracts_to_rvl_requests_status-changes");
        assertThat(queueDefault("onec", "km-contract-submissions"))
                .isEqualTo("rvl_contracts_to_1c-usk_km-contract-changes");
        assertThat(queueDefault("onec", "km-contract-registrations"))
                .isEqualTo("1c-usk_to_rvl_contracts_km-contract-changes");
        assertThat(queueDefault("onec", "lm-policy-requests")).isEqualTo("rvl_contracts_to_1c-usk_lm-policy-changes");
        assertThat(queueDefault("onec", "lm-policy-issues")).isEqualTo("1c-usk_to_rvl_contracts_lm-policy-changes");
    }

    /** Умолчание плейсхолдера {@code ${ИМЯ:значение}} — текст после первого двоеточия. */
    private static String queueDefault(String broker, String queue) {
        String placeholder = String.valueOf(application.getProperty("projectx.brokers." + broker + ".queues." + queue));
        return placeholder.substring(placeholder.indexOf(':') + 1, placeholder.length() - 1);
    }
}
