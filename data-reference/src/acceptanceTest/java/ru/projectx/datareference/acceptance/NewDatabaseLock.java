package ru.projectx.datareference.acceptance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.migration.MigrationLockDataSource;

@Component
public class NewDatabaseLock {

    private static final String LIVING_INSTANCE = "другой экземпляр";
    private static final String CREATE_LOCK_TABLE = """
            create table databasechangeloglock (
              id integer primary key, locked boolean not null, lockgranted timestamp, lockedby varchar(255))
            """;
    private static final String HOLD_LOCK = """
            insert into databasechangeloglock (id, locked, lockgranted, lockedby) values (1, true, ?, ?)
            """;
    private static final String RELEASE_LOCK = """
            update databasechangeloglock set locked = false, lockgranted = null, lockedby = null where id = 1
            """;
    private static final String LOCK_HELD = "select locked from databasechangeloglock where id = 1";
    private static final String TAKE_SERVICE_LOCK = "select pg_advisory_lock(?)";

    private final NewDatabase database;
    private @Nullable Connection livingInstance;

    public NewDatabaseLock(NewDatabase database) {
        this.database = database;
    }

    public void holdByLivingInstance() throws SQLException {
        Connection held = DriverManager.getConnection(database.url(), database.username(), database.password());
        livingInstance = held;
        try (PreparedStatement statement = held.prepareStatement(TAKE_SERVICE_LOCK)) {
            statement.setLong(1, MigrationLockDataSource.MIGRATION_LOCK_KEY);
            statement.execute();
        }
        leaveRow(LIVING_INSTANCE, LocalDateTime.now());
    }

    public void leaveFromDeadInstance(String lockedBy, LocalDateTime granted) {
        leaveRow(lockedBy, granted);
    }

    public void releaseByLivingInstance() throws SQLException {
        database.jdbc().sql(RELEASE_LOCK).update();
        closeLivingInstance();
    }

    public boolean held() {
        return database.jdbc().sql(LOCK_HELD).query(Boolean.class).single();
    }

    public void closeLivingInstance() throws SQLException {
        Optional<Connection> held = Optional.ofNullable(livingInstance);
        livingInstance = null;
        if (held.isPresent()) {
            held.get().close();
        }
    }

    private void leaveRow(String lockedBy, LocalDateTime granted) {
        database.jdbc().sql(CREATE_LOCK_TABLE).update();
        database.jdbc().sql(HOLD_LOCK).params(granted, lockedBy).update();
    }
}
