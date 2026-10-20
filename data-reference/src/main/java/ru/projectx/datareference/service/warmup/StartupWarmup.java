package ru.projectx.datareference.service.warmup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.WarmupProperties;

@Component
public class StartupWarmup implements ApplicationRunner {

    private final ReferenceWarmup warmup;
    private final WarmupJournal journal;
    private final WarmupProperties properties;
    private final Clock clock;

    public StartupWarmup(ReferenceWarmup warmup, WarmupJournal journal, WarmupProperties properties, Clock clock) {
        this.warmup = warmup;
        this.journal = journal;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Instant started = clock.instant();
        WarmupOutcome outcome = warmup.warm(started.plus(properties.timeout()));
        journal.record(outcome, Duration.between(started, clock.instant()));
    }
}
