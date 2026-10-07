package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;

/**
 * Пул, который собрал Spring Boot, становится пулом приложения внутри источника соединений с отдельным пулом
 * REST-чтений. Остальные бины не трогаются.
 */
class RestReadDataSourcePostProcessorTest {

    private static final RestReadPoolProperties SETTINGS =
            new RestReadPoolProperties(3, Duration.ofMillis(1200), 4, 1);

    private final RestReadDataSourcePostProcessor postProcessor = new RestReadDataSourcePostProcessor(settings());

    @Test
    void hikariPool_becomesApplicationPoolBesideRestReadPool() {
        HikariDataSource application = new HikariDataSource();
        application.setJdbcUrl("jdbc:postgresql://db:5432/data_reference");

        Object processed = postProcessor.postProcessAfterInitialization(application, "dataSource");

        assertThat(processed).isInstanceOfSatisfying(RestReadDataSource.class, dataSource -> {
            assertThat(dataSource.applicationPool()).isSameAs(application);
            assertThat(dataSource.restReadPool().getJdbcUrl()).isEqualTo("jdbc:postgresql://db:5432/data_reference");
            assertThat(dataSource.restReadPool().getMaximumPoolSize()).isEqualTo(3);
        });
    }

    /** Пул REST-чтений — тоже бин с пулом HikariCP; он остаётся как есть. */
    @Test
    void otherHikariPool_staysAsIs() {
        HikariDataSource restRead = new HikariDataSource();

        assertThat(postProcessor.postProcessAfterInitialization(restRead, "restReadPool")).isSameAs(restRead);
    }

    @Test
    void otherBean_staysAsIs() {
        Object bean = new Object();

        assertThat(postProcessor.postProcessAfterInitialization(bean, "dataSource")).isSameAs(bean);
    }

    /** Первым из обработчиков: иначе другая обёртка источника спрятала бы пул Boot, и пула REST-чтений не было бы. */
    @Test
    void runsBeforeOtherPostProcessors() {
        assertThat(postProcessor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    private static StaticListableBeanFactory settings() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("restReadPoolProperties", SETTINGS);
        beans.addBean("environment", new MockEnvironment());
        return beans;
    }
}
