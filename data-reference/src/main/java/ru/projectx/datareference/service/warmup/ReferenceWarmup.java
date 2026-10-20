package ru.projectx.datareference.service.warmup;

import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import ru.projectx.datareference.config.WarmupProperties;

@Component
public class ReferenceWarmup {

    private static final String DATABASE_FAILURE = "отказ базы: ";

    private final LocalApi localApi;
    private final WarmupKeysReader keys;
    private final WarmupRun run;
    private final WarmupProperties properties;

    public ReferenceWarmup(LocalApi localApi, WarmupKeysReader keys, WarmupRun run, WarmupProperties properties) {
        this.localApi = localApi;
        this.keys = keys;
        this.run = run;
        this.properties = properties;
    }

    public WarmupOutcome warm(Instant deadline) {
        return localApi.connect(properties.timeout())
                .map(api -> warmThrough(api, deadline))
                .orElseGet(WarmupOutcome::withoutApiServer);
    }

    private WarmupOutcome warmThrough(LocalApiClient client, Instant deadline) {
        try (LocalApiClient api = client) {
            return run.through(api, keys.read(properties.recordsPerReference()).paths(), deadline);
        } catch (DataAccessException | TransactionException failure) {
            return WarmupOutcome.interrupted(DATABASE_FAILURE + failure.getClass().getSimpleName(), 0);
        }
    }
}
