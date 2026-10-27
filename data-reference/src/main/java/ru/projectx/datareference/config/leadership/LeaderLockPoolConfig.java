package ru.projectx.datareference.config.leadership;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import ru.projectx.datareference.config.DatabaseSessionTimeouts;
import ru.projectx.datareference.config.restread.RestReadDataSource;

@Configuration(proxyBeanMethods = false)
public class LeaderLockPoolConfig {

    public static final String LEADER_LOCK_POOL = "leaderLockPool";

    @Bean(name = LEADER_LOCK_POOL, defaultCandidate = false, destroyMethod = "close")
    HikariDataSource leaderLockPool(DataSource dataSource, Environment environment,
            LeaderLockPoolProperties settings, DatabaseSessionTimeouts sessionTimeouts) throws SQLException {

        HikariDataSource application = dataSource.unwrap(RestReadDataSource.class).applicationPool();
        return LeaderLockPools.leaderLockPool(application, environment, settings, sessionTimeouts);
    }
}
