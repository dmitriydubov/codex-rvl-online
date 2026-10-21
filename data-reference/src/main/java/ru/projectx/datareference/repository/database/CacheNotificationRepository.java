package ru.projectx.datareference.repository.database;

import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CacheNotificationRepository {

    private static final String NOTIFY = "select pg_notify(:channel, :payload)";

    private final JdbcClient jdbc;

    public CacheNotificationRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public void notify(String channel, String payload) {
        jdbc.sql(NOTIFY)
                .param("channel", channel)
                .param("payload", payload)
                .query()
                .singleRow();
    }
}
