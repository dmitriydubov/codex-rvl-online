package ru.projectx.contracts.service.leadership;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.DatabaseTimeoutsProperties;
import ru.projectx.contracts.repository.SessionSettingsRepository;
import ru.projectx.contracts.service.SessionTimeoutsCheck;

@Component
public class LeaderLockSessionCheck implements ApplicationRunner {

    private final SessionTimeoutsCheck check;

    @Autowired
    public LeaderLockSessionCheck(DatabaseTimeoutsProperties timeouts, LeaderLockConnections leaderLock) {
        this(timeouts, new SessionSettingsRepository(JdbcClient.create(leaderLock.dataSource())));
    }

    LeaderLockSessionCheck(DatabaseTimeoutsProperties timeouts, SessionSettingsRepository leaderLockSession) {
        this.check = SessionTimeoutsCheck.forPoolWithoutLockTimeout(LeaderLockPool.POOL_NAME, timeouts,
                leaderLockSession);
    }

    @Override
    public void run(@Nullable ApplicationArguments arguments) {
        check.run();
    }
}
