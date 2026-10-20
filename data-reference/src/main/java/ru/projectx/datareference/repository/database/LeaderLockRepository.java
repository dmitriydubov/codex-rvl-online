package ru.projectx.datareference.repository.database;

import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LeaderLockRepository {

    private static final String HOLD_IF_OWNED = """
            select 1
              from leader_lock
             where region = :group
               and lock_key = :key
               and client_id = :instance
               for share
            """;

    private final JdbcClient jdbc;

    public LeaderLockRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public boolean holdIfOwned(String group, String key, String instanceId) {
        return !jdbc.sql(HOLD_IF_OWNED)
                .param("group", group)
                .param("key", key)
                .param("instance", instanceId)
                .query(Integer.class)
                .list()
                .isEmpty();
    }
}
