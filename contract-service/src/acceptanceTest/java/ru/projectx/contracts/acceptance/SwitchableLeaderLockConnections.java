package ru.projectx.contracts.acceptance;

import javax.sql.DataSource;
import ru.projectx.contracts.service.leadership.LeaderLockConnections;

final class SwitchableLeaderLockConnections implements LeaderLockConnections {

    private final SwitchableDataSource connections;

    SwitchableLeaderLockConnections(LeaderLockConnections pool) {
        this.connections = new SwitchableDataSource(pool.dataSource());
    }

    SwitchableDataSource connections() {
        return connections;
    }

    @Override
    public DataSource dataSource() {
        return connections;
    }
}
