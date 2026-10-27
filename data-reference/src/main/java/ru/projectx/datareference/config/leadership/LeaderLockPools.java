package ru.projectx.datareference.config.leadership;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.core.env.Environment;
import ru.projectx.datareference.config.ApplicationPoolCopy;
import ru.projectx.datareference.config.DatabaseSessionTimeouts;
import ru.projectx.datareference.config.SessionStartupOptions;

public final class LeaderLockPools {

    public static final String POOL_NAME = "leader-lock";
    public static final String APPLICATION_NAME = "data-reference-leader-lock";

    private static final String APPLICATION_NAME_PROPERTY = "ApplicationName";

    private LeaderLockPools() {
    }

    public static HikariDataSource leaderLockPool(HikariDataSource application, Environment environment,
            LeaderLockPoolProperties settings, DatabaseSessionTimeouts sessionTimeouts) {

        HikariDataSource leaderLock = ApplicationPoolCopy.named(POOL_NAME, application, environment);
        leaderLock.setMaximumPoolSize(settings.maximumPoolSize());
        leaderLock.setMinimumIdle(settings.maximumPoolSize());
        SessionStartupOptions.set(leaderLock, sessionTimeouts.withoutLockWait());
        leaderLock.addDataSourceProperty(APPLICATION_NAME_PROPERTY, APPLICATION_NAME);
        return leaderLock;
    }
}
