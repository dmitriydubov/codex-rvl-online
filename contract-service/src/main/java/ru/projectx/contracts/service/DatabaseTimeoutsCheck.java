package ru.projectx.contracts.service;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.DatabaseTimeoutsProperties;
import ru.projectx.contracts.repository.database.SessionSettingsRepository;

@Component
public class DatabaseTimeoutsCheck implements ApplicationRunner {

    static final String APPLICATION_POOL = "application";

    private final SessionTimeoutsCheck check;

    public DatabaseTimeoutsCheck(DatabaseTimeoutsProperties timeouts, SessionSettingsRepository sessionSettings) {
        this.check = SessionTimeoutsCheck.forApplicationPool(APPLICATION_POOL, timeouts, sessionSettings);
    }

    @Override
    public void run(@Nullable ApplicationArguments arguments) {
        check.run();
    }
}
