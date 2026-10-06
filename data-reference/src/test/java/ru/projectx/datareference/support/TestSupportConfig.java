package ru.projectx.datareference.support;

import jakarta.servlet.Filter;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;

/** Вспомогательные бины, нужные только тестам. */
@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfig {

    @Bean
    public DatabaseCleaner databaseCleaner(DataSource dataSource, CacheManager cacheManager) {
        return new DatabaseCleaner(dataSource, cacheManager);
    }

    @Bean
    public TestJdbc testJdbc(DataSource dataSource) {
        return new TestJdbc(dataSource);
    }

    /**
     * Клиент к стеку MVC поверх контекста приложения — то, что давала бы
     * {@code @AutoConfigureMockMvc}, будь модуль {@code spring-boot-webmvc-test}
     * доступен в Nexus контура. Сам {@link MockMvcTester} — часть {@code spring-test},
     * и без Boot он теряет одно: фильтры из контекста сами не подключаются.
     * Поэтому они добавляются здесь, все и в порядке {@code @Order}, как их
     * зарегистрировал бы контейнер сервлетов. Иначе тест на {@code X-Request-Id}
     * проверял бы фильтр, которого в цепочке нет.
     */
    @Bean
    public MockMvcTester mockMvcTester(WebApplicationContext context, List<Filter> filters) {
        return MockMvcTester.from(context, builder -> builder
                .addFilters(filters.toArray(Filter[]::new))
                .build());
    }
}
