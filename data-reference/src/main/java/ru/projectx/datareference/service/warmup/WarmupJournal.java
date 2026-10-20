package ru.projectx.datareference.service.warmup;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WarmupJournal {

    private static final Logger log = LoggerFactory.getLogger(WarmupJournal.class);

    public void record(WarmupOutcome outcome, Duration took) {
        log.atLevel(outcome.result().level()).log("прогрев {}: {}; чтений {}, длительность {} мс",
                outcome.result().word(), outcome.reason(), outcome.reads(), took.toMillis());
    }
}
