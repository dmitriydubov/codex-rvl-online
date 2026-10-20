package ru.projectx.datareference.service.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.query.BadJpqlGrammarException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.repository.catalog.Mkb10Repository;
import ru.projectx.datareference.repository.policy.PolicyRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class ExpiredReadDeadlineTest {

    private static final int READ_LIMIT_SECONDS = 1;
    private static final Duration DEADLINE_WAIT = Duration.ofSeconds(5);
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-01T09:00:00Z");
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2030, 12, 31);
    private static final String NO_SQL_STATE = "нет";

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PolicyRepository policies;

    @Autowired
    private Mkb10Repository diagnoses;

    @Autowired
    private DatabaseOutages outages;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private Policy stored;

    @BeforeEach
    void storePolicy() {
        databaseCleaner.clear();
        var details = new Policy.Details(UUID.randomUUID(), "П-0001", "Тестов", "Тест", "Тестович",
                LocalDate.of(1990, 1, 1), "Мужской", "test@example.org", PERIOD_START, PERIOD_END, true, false);
        stored = policies.save(Policy.create(UUID.randomUUID(), new Policy.Snapshot(details, List.of(), List.of()),
                RECEIVED_AT));
    }

    @Test
    void derivedQueryCreatedAfterDeadline_hidesExpiredDeadlineUnderGrammarError_andIsAnOutage() {
        RuntimeException failure = failureAfterDeadline(() -> policies.findByExternalId(stored.externalId()));

        assertThat(failure).isInstanceOf(BadJpqlGrammarException.class)
                .hasRootCauseInstanceOf(TransactionTimedOutException.class);
        assertThat(outages.recognize(failure)).hasValue(
                new DatabaseOutages.Outage("BadJpqlGrammarException/TransactionTimedOutException", NO_SQL_STATE));
    }

    @Test
    void jpqlQueryCreatedAfterDeadline_failsWithExpiredDeadlineItself_andIsAnOutage() {
        RuntimeException failure = failureAfterDeadline(() -> diagnoses.findByCodes(List.of("A00")));

        assertThat(failure).isExactlyInstanceOf(TransactionTimedOutException.class);
        assertThat(outages.recognize(failure))
                .hasValue(new DatabaseOutages.Outage("TransactionTimedOutException", NO_SQL_STATE));
    }

    @Test
    void nativeQueryCreatedAfterDeadline_failsWithExpiredDeadlineItself_andIsAnOutage() {
        RuntimeException failure = failureAfterDeadline(
                () -> policies.countPersonalDataBeyond(PERIOD_START, RECEIVED_AT));

        assertThat(failure).isExactlyInstanceOf(TransactionTimedOutException.class);
        assertThat(outages.recognize(failure))
                .hasValue(new DatabaseOutages.Outage("TransactionTimedOutException", NO_SQL_STATE));
    }

    @Test
    void findByIdAfterDeadline_isAnOutage() {
        RuntimeException failure = failureAfterDeadline(() -> policies.findById(stored.id()));

        assertThat(outages.recognize(failure))
                .hasValue(new DatabaseOutages.Outage("JpaSystemException/TransactionException", NO_SQL_STATE));
    }

    @Test
    void lazyLoadingAfterDeadline_isAnOutage() {
        RuntimeException failure = failureAfterDeadline(
                () -> policies.findById(stored.id()).orElseThrow(), policy -> policy.programs().size());

        assertThat(outages.recognize(failure))
                .hasValue(new DatabaseOutages.Outage("JpaSystemException/TransactionException", NO_SQL_STATE));
    }

    private RuntimeException failureAfterDeadline(Supplier<?> read) {
        return failureAfterDeadline(() -> stored, ignored -> read.get());
    }

    private <T> RuntimeException failureAfterDeadline(Supplier<T> beforeDeadline, Function<T, ?> afterDeadline) {
        TransactionTemplate readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setReadOnly(true);
        readTransaction.setTimeout(READ_LIMIT_SECONDS);
        return catchRuntimeException(() -> readTransaction.execute(status -> {
            T loaded = beforeDeadline.get();
            Instant deadline = currentDeadline();
            await().atMost(DEADLINE_WAIT).until(() -> deadline.isBefore(Instant.now()));
            return afterDeadline.apply(loaded);
        }));
    }

    private static Instant currentDeadline() {
        return TransactionSynchronizationManager.getResourceMap().values().stream()
                .filter(ResourceHolderSupport.class::isInstance)
                .map(ResourceHolderSupport.class::cast)
                .map(ResourceHolderSupport::getDeadline)
                .flatMap(deadline -> Optional.ofNullable(deadline).stream())
                .map(Date::toInstant)
                .findFirst()
                .orElseThrow();
    }
}
