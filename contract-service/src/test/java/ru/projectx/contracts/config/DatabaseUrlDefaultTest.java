package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Умолчание адреса базы несёт {@code logServerErrorDetail=false}: свойства пула до соединения миграций не доходят,
 * и без параметра в адресе ошибка миграции попала бы в журнал со значениями строки. Тесты с базой адрес подменяют,
 * поэтому умолчание проверяется прямо в {@code application.yml}.
 */
class DatabaseUrlDefaultTest {

    @Test
    void defaultDatabaseUrl_hidesServerErrorDetail() throws IOException {
        PropertySource<?> application = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml")).getFirst();

        assertThat(application.getProperty("spring.datasource.url")).asString()
                .startsWith("${DB_URL:jdbc:postgresql://")
                .endsWith("?logServerErrorDetail=false}");
    }
}
