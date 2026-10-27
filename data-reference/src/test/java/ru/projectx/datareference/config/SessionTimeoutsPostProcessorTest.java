package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class SessionTimeoutsPostProcessorTest {

    private static final String CONFIGURED_OPTIONS =
            "-c lock_timeout=5s -c statement_timeout=30s -c idle_in_transaction_session_timeout=1min";

    private final SessionTimeoutsPostProcessor postProcessor = new SessionTimeoutsPostProcessor(configured());

    @Test
    void bootPool_opensSessionsWithConfiguredTimeouts() {
        HikariDataSource application = new HikariDataSource();

        Object processed = postProcessor.postProcessBeforeInitialization(application, "dataSource");

        assertThat(processed).isSameAs(application);
        assertThat(application.getDataSourceProperties()).containsEntry("options", CONFIGURED_OPTIONS);
    }

    @Test
    void bootPool_startupOptionsFromEnvironment_giveWayToConfiguredTimeouts() {
        HikariDataSource application = new HikariDataSource();
        application.addDataSourceProperty("options", "-c lock_timeout=9s");

        postProcessor.postProcessBeforeInitialization(application, "dataSource");

        assertThat(application.getDataSourceProperties()).containsEntry("options", CONFIGURED_OPTIONS);
    }

    @Test
    void otherHikariPool_staysAsIs() {
        HikariDataSource restRead = new HikariDataSource();

        Object processed = postProcessor.postProcessBeforeInitialization(restRead, "restReadPool");

        assertThat(processed).isSameAs(restRead);
        assertThat(restRead.getDataSourceProperties()).doesNotContainKey("options");
    }

    @Test
    void otherBeanNamedDataSource_staysAsIs() {
        Object bean = new Object();

        assertThat(postProcessor.postProcessBeforeInitialization(bean, "dataSource")).isSameAs(bean);
    }

    @Test
    void timeouts_areReadOnlyWhenTheBootPoolArrives() {
        SessionTimeoutsPostProcessor withoutTimeouts =
                new SessionTimeoutsPostProcessor(new StaticListableBeanFactory().getBeanProvider(DatabaseSessionTimeouts.class));

        assertThatNoException().isThrownBy(
                () -> withoutTimeouts.postProcessBeforeInitialization(new HikariDataSource(), "restReadPool"));
    }

    private static ObjectProvider<DatabaseSessionTimeouts> configured() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("databaseSessionTimeouts", new DatabaseSessionTimeouts("5s", "30s", "1min"));
        return beans.getBeanProvider(DatabaseSessionTimeouts.class);
    }
}
