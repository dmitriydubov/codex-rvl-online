package ru.projectx.datareference.config.restread;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;

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
