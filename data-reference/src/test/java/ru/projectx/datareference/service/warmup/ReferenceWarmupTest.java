package ru.projectx.datareference.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import ru.projectx.datareference.config.WarmupProperties;
import ru.projectx.datareference.repository.ReferenceId;

@ExtendWith(MockitoExtension.class)
class ReferenceWarmupTest {

    private static final Instant DEADLINE = Instant.parse("2026-10-20T09:00:30Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int RECORDS = 100;
    private static final UUID PROGRAM = UUID.fromString("2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a");

    @Mock
    private LocalApi localApi;
    @Mock
    private LocalApiClient api;
    @Mock
    private WarmupKeysReader keys;
    @Mock
    private WarmupRun run;

    private ReferenceWarmup warmup() {
        return new ReferenceWarmup(localApi, keys, run, new WarmupProperties(TIMEOUT, RECORDS));
    }

    @Test
    void withApiServer_readsThePathsOfTheKeysAndClosesTheClient() {
        WarmupKeys found = new WarmupKeys(List.of(), List.of(), List.of(new ReferenceId(PROGRAM)), List.of());
        when(localApi.connect(TIMEOUT)).thenReturn(Optional.of(api));
        when(keys.read(RECORDS)).thenReturn(found);
        when(run.through(api, found.paths(), DEADLINE)).thenReturn(WarmupOutcome.completed(1));

        assertThat(warmup().warm(DEADLINE)).isEqualTo(WarmupOutcome.completed(1));
        verify(api).close();
    }

    @Test
    void withoutApiServer_readsNothing() {
        when(localApi.connect(TIMEOUT)).thenReturn(Optional.empty());

        assertThat(warmup().warm(DEADLINE)).isEqualTo(WarmupOutcome.withoutApiServer());
        verifyNoInteractions(keys, run);
    }

    @Test
    void databaseFailureWhileReadingKeys_interruptsTheWarmupAndClosesTheClient() {
        when(localApi.connect(TIMEOUT)).thenReturn(Optional.of(api));
        when(keys.read(RECORDS)).thenThrow(new QueryTimeoutException("canceling statement due to lock timeout"));

        assertThat(warmup().warm(DEADLINE))
                .isEqualTo(WarmupOutcome.interrupted("отказ базы: QueryTimeoutException", 0));
        verify(api).close();
        verifyNoInteractions(run);
    }

    @Test
    void transactionFailureWhileReadingKeys_interruptsTheWarmup() {
        when(localApi.connect(TIMEOUT)).thenReturn(Optional.of(api));
        when(keys.read(RECORDS)).thenThrow(new CannotCreateTransactionException("connection refused"));

        assertThat(warmup().warm(DEADLINE))
                .isEqualTo(WarmupOutcome.interrupted("отказ базы: CannotCreateTransactionException", 0));
    }
}
