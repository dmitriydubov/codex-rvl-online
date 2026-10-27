package ru.projectx.contracts.acceptance;

import javax.sql.DataSource;
import ru.projectx.contracts.service.leadership.LeaderLockConnections;
import ru.projectx.contracts.service.leadership.LeaderLockPool;

final class SwitchableLeaderLockConnections implements LeaderLockConnections, AutoCloseable {

    private final LeaderLockConnections pool;
    private final SwitchableDataSource connections;

    SwitchableLeaderLockConnections(LeaderLockConnections pool) {
        this.pool = pool;
        this.connections = new SwitchableDataSource(pool.dataSource());
    }

    SwitchableDataSource connections() {
        return connections;
    }

    @Override
    public DataSource dataSource() {
        return connections;
    }

    @Override
    public void close() {
        if (pool instanceof LeaderLockPool hikari) {
            hikari.close();
        }
    }
}
