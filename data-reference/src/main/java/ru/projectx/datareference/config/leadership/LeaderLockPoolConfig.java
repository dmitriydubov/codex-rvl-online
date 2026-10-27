package ru.projectx.datareference.config.leadership;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import ru.projectx.datareference.config.DatabaseSessionTimeouts;
import ru.projectx.datareference.config.restread.ApplicationPool;

@Configuration(proxyBeanMethods = false)
public class LeaderLockPoolConfig {

    public static final String LEADER_LOCK_POOL = "leaderLockPool";

    @Bean(name = LEADER_LOCK_POOL, defaultCandidate = false, destroyMethod = "close")
    HikariDataSource leaderLockPool(ApplicationPool application, Environment environment,
            LeaderLockPoolProperties settings, DatabaseSessionTimeouts sessionTimeouts) {

        return LeaderLockPools.leaderLockPool(application.pool(), environment, settings, sessionTimeouts);
    }
}
