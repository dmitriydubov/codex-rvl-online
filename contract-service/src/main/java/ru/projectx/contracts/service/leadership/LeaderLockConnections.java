package ru.projectx.contracts.service.leadership;

import javax.sql.DataSource;

public interface LeaderLockConnections {

    DataSource dataSource();
}
