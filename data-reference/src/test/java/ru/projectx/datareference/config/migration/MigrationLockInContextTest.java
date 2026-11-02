package ru.projectx.datareference.config.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class MigrationLockInContextTest {

    private static final long KEY_IN_OPERATIONS_GUIDE = 7_311_094_201_825_640_017L;
    private static final String LOCK_HOLDER = """
            select a.pid
              from pg_locks l join pg_stat_activity a on a.pid = l.pid
             where l.locktype = 'advisory' and l.granted and l.objsubid = 1
               and ((l.classid::bigint << 32) | l.objid::bigint) = %d
            """.formatted(KEY_IN_OPERATIONS_GUIDE);

    @Autowired
    private SpringLiquibase liquibase;

    @Autowired
    private DataSource dataSource;

    @Test
    void afterMigrations_liquibaseDataSourceIsNoLongerBehindTheMigrationLock() {
        assertThat(liquibase.getDataSource()).isNotInstanceOf(MigrationLockDataSource.class);
    }

    @Test
    void keyInOperationsGuide_isTheMigrationLockKey() {
        assertThat(KEY_IN_OPERATIONS_GUIDE).isEqualTo(MigrationLockDataSource.MIGRATION_LOCK_KEY);
    }

    @Test
    void lockHolderQueryOfOperationsGuide_findsTheSessionHoldingTheMigrationLock() throws SQLException {
        try (Connection holder = new MigrationLockDataSource(dataSource).getConnection()) {
            assertThat(JdbcClient.create(dataSource).sql(LOCK_HOLDER).query(Integer.class).list())
                    .containsExactly(backendPid(holder));
        }
    }

    private static int backendPid(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select pg_backend_pid()")) {
            result.next();
            return result.getInt(1);
        }
    }
}
