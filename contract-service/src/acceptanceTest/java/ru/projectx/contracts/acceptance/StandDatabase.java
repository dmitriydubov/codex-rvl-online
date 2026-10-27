package ru.projectx.contracts.acceptance;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Component
public class StandDatabase {

    private final JdbcClient jdbc;

    public StandDatabase(PostgreSQLContainer database) {
        this.jdbc = JdbcClient.create(new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
    }

    JdbcClient jdbc() {
        return jdbc;
    }

    boolean receivedIntoInbox(String messageId) {
        return !jdbc.sql("select 1 from inbox_messages where message_id = :id")
                .param("id", UUID.fromString(messageId))
                .query(Integer.class)
                .list()
                .isEmpty();
    }
}
