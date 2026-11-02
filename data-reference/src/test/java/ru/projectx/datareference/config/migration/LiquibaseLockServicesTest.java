package ru.projectx.datareference.config.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import liquibase.lockservice.StandardLockService;
import org.junit.jupiter.api.Test;

class LiquibaseLockServicesTest {

    @Test
    void postgresConnection_getsLiquibaseStandardLockService() throws SQLException {
        Connection connection = mock(Connection.class, RETURNS_MOCKS);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class, RETURNS_MOCKS);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getURL()).thenReturn("jdbc:postgresql://localhost:5432/data_reference");

        assertThat(LiquibaseLockServices.of(connection)).isInstanceOf(StandardLockService.class);
    }
}
