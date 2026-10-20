package ru.projectx.datareference.repository.database;

import java.time.Instant;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseClockRepository {

    private static final String CURRENT_MOMENT = "select clock_timestamp()";

    private final JdbcClient jdbc;

    public DatabaseClockRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public Instant currentMoment() {
        return jdbc.sql(CURRENT_MOMENT).query(OffsetDateTime.class).single().toInstant();
    }
}
