package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
}
