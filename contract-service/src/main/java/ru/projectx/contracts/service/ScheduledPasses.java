package ru.projectx.contracts.service;

import org.slf4j.Logger;
import ru.projectx.contracts.service.database.SqlStates;

public final class ScheduledPasses {

    private ScheduledPasses() {
    }

    public static void run(Logger log, String pass, Runnable body) {
        try {
            body.run();
        }
        catch (RuntimeException failure) {
            log.warn("проход {} не выполнен: {}, SQLSTATE {}", pass, failure.getClass().getName(),
                    SqlStates.deepestOrNone(failure));
        }
    }
}
