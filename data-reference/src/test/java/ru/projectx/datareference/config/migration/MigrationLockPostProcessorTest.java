package ru.projectx.datareference.config.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;

class MigrationLockPostProcessorTest {

    private final MigrationLockPostProcessor postProcessor = new MigrationLockPostProcessor();

    @Test
    void liquibaseBeforeMigrations_getsItsConnectionsThroughTheMigrationLock() {
        DataSource migrations = mock(DataSource.class);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(migrations);

        Object processed = postProcessor.postProcessBeforeInitialization(liquibase, "liquibase");

        assertThat(processed).isSameAs(liquibase);
        assertThat(liquibase.getDataSource()).isInstanceOfSatisfying(MigrationLockDataSource.class,
                locked -> assertThat(locked.getTargetDataSource()).isSameAs(migrations));
    }

    @Test
    void liquibaseAlreadyBehindTheMigrationLock_isNotWrappedTwice() {
        MigrationLockDataSource locked = new MigrationLockDataSource(mock(DataSource.class));
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(locked);

        postProcessor.postProcessBeforeInitialization(liquibase, "liquibase");

        assertThat(liquibase.getDataSource()).isSameAs(locked);
    }

    @Test
    void otherBeans_areReturnedUnchanged() {
        DataSource dataSource = mock(DataSource.class);

        assertThat(postProcessor.postProcessBeforeInitialization(dataSource, "dataSource")).isSameAs(dataSource);
    }

    @Test
    void liquibaseAfterMigrations_getsBackItsOwnDataSource() {
        DataSource migrations = mock(DataSource.class);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(new MigrationLockDataSource(migrations));

        Object processed = postProcessor.postProcessAfterInitialization(liquibase, "liquibase");

        assertThat(processed).isSameAs(liquibase);
        assertThat(liquibase.getDataSource()).isSameAs(migrations);
    }

    @Test
    void liquibaseWithoutMigrationLockAfterMigrations_keepsItsDataSource() {
        DataSource migrations = mock(DataSource.class);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(migrations);

        postProcessor.postProcessAfterInitialization(liquibase, "liquibase");

        assertThat(liquibase.getDataSource()).isSameAs(migrations);
    }

    @Test
    void otherBeansAfterInitialization_areReturnedUnchanged() {
        DataSource dataSource = new MigrationLockDataSource(mock(DataSource.class));

        assertThat(postProcessor.postProcessAfterInitialization(dataSource, "dataSource")).isSameAs(dataSource);
    }
}
