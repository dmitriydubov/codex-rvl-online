package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.PriorityOrdered;

@ExtendWith(OutputCaptureExtension.class)
class SessionTimeoutsPostProcessorTest {

    private static final String CONFIGURED_OPTIONS =
            "-c lock_timeout=5s -c statement_timeout=30s -c idle_in_transaction_session_timeout=1min";
    private static final String REPLACED = "стартовый параметр options пула";

    private final SessionTimeoutsPostProcessor postProcessor = new SessionTimeoutsPostProcessor(configured());

    @Test
    void bootPool_opensSessionsWithConfiguredTimeouts(CapturedOutput output) {
        HikariDataSource application = new HikariDataSource();

        Object processed = postProcessor.postProcessBeforeInitialization(application, "dataSource");

        assertThat(processed).isSameAs(application);
        assertThat(application.getDataSourceProperties()).containsEntry("options", CONFIGURED_OPTIONS);
        assertThat(ownRecords(output)).isEmpty();
    }

    @Test
    void bootPool_startupOptionsFromEnvironment_giveWayToConfiguredTimeoutsWithWarning(CapturedOutput output) {
        HikariDataSource application = new HikariDataSource();
        application.setPoolName("application");
        application.addDataSourceProperty("options", "-c lock_timeout=9s");

        postProcessor.postProcessBeforeInitialization(application, "dataSource");

        assertThat(application.getDataSourceProperties()).containsEntry("options", CONFIGURED_OPTIONS);
        assertThat(ownRecords(output)).singleElement().satisfies(record -> assertThat(record)
                .contains("WARN")
                .contains("стартовый параметр options пула application из spring.datasource.hikari заменён "
                        + "таймаутами сеанса projectx.database.session-timeouts")
                .doesNotContain("9s"));
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

    @Test
    void runsAfterPropertyBinding_soItIsNotPriorityOrdered() {
        assertThat(postProcessor).isNotInstanceOf(PriorityOrdered.class);
    }

    private static List<String> ownRecords(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains(REPLACED)).toList();
    }

    private static ObjectProvider<DatabaseSessionTimeouts> configured() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("databaseSessionTimeouts", new DatabaseSessionTimeouts("5s", "30s", "1min"));
        return beans.getBeanProvider(DatabaseSessionTimeouts.class);
    }
}
