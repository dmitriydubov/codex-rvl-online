package ru.projectx.datareference.service.warmup;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

@Component
public class WarmupJournal {

    private static final Logger log = LoggerFactory.getLogger(WarmupJournal.class);

    private static final Map<WarmupResult, String> WORDS = Map.of(
            WarmupResult.COMPLETED, "закончен",
            WarmupResult.INTERRUPTED, "прерван",
            WarmupResult.SKIPPED, "пропущен");
    private static final Map<WarmupResult, Level> LEVELS = Map.of(
            WarmupResult.COMPLETED, Level.INFO,
            WarmupResult.INTERRUPTED, Level.WARN,
            WarmupResult.SKIPPED, Level.INFO);

    public void record(WarmupOutcome outcome, Duration took) {
        log.atLevel(LEVELS.get(outcome.result())).log("прогрев {}: {}; чтений {}, длительность {} мс",
                WORDS.get(outcome.result()), outcome.reason(), outcome.reads(), took.toMillis());
    }
}
