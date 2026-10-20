package ru.projectx.datareference.service.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.query.BadJpqlGrammarException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.repository.policy.PolicyRepository;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
@Import(ReadDeadlineUnderDerivedQueryTest.LateReader.class)
class ReadDeadlineUnderDerivedQueryTest {

    private static final int READ_LIMIT_SECONDS = 1;
    private static final Duration DEADLINE_WAIT = Duration.ofSeconds(5);
    private static final UUID EXTERNAL_ID = UUID.fromString("9e6ee30d-2275-417d-a44c-039d3b8c2ac5");

    @Autowired
    private LateReader reader;

    @Autowired
    private DatabaseOutages outages;

    @Test
    void derivedQueryCreatedAfterReadDeadline_hidesExpiredDeadlineUnderGrammarError() {
        RuntimeException failure = catchRuntimeException(() -> reader.policyAfterDeadline(EXTERNAL_ID));

        assertThat(failure).isInstanceOf(BadJpqlGrammarException.class)
                .hasRootCauseInstanceOf(TransactionTimedOutException.class);
    }

    @Test
    void derivedQueryCreatedAfterReadDeadline_isRecognizedAsTemporaryOutage() {
        RuntimeException failure = catchRuntimeException(() -> reader.policyAfterDeadline(EXTERNAL_ID));

        assertThat(outages.recognize(failure))
                .hasValue(new DatabaseOutages.Outage("BadJpqlGrammarException/TransactionTimedOutException", "нет"));
    }

    static class LateReader {

        private final PolicyRepository policies;

        LateReader(PolicyRepository policies) {
            this.policies = policies;
        }

        @Transactional(readOnly = true, timeout = READ_LIMIT_SECONDS)
        public Optional<Policy> policyAfterDeadline(UUID externalId) {
            Date deadline = currentDeadline();
            await().atMost(DEADLINE_WAIT).until(() -> deadline.toInstant().isBefore(Instant.now()));
            return policies.findByExternalId(externalId);
        }

        private static Date currentDeadline() {
            return TransactionSynchronizationManager.getResourceMap().values().stream()
                    .filter(ResourceHolderSupport.class::isInstance)
                    .map(ResourceHolderSupport.class::cast)
                    .map(ResourceHolderSupport::getDeadline)
                    .flatMap(deadline -> Optional.ofNullable(deadline).stream())
                    .findFirst()
                    .orElseThrow();
        }
    }
}
