package ru.projectx.datareference.service.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionOperations;
import ru.projectx.datareference.entity.InboundOutcome;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class InboundTransitionCommitTest {

    private static final ParseFailure.Temporary TIMEOUT = new ParseFailure.Temporary(
            "временный отказ базы данных: QueryTimeoutException, SQLSTATE 57014", "QueryTimeoutException", "57014");

    private static final InboundOutcome NEW = new InboundOutcome(InboundStatus.NEW, 0, null, null, null, null);

    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void committedTransition_isCountedOnce() {
        UUID messageId = stored();
        double before = transitions(InboundStatus.PROCESSED);

        transactions.executeWithoutResult(status -> inboundMessageService.markProcessed(messageId));

        assertThat(transitions(InboundStatus.PROCESSED)).isEqualTo(before + 1);
    }

    @Test
    void rolledBackTransition_isNotCounted() {
        UUID messageId = stored();
        double before = transitions(InboundStatus.PROCESSED);

        transactions.executeWithoutResult(status -> {
            inboundMessageService.markProcessed(messageId);
            status.setRollbackOnly();
        });

        assertThat(transitions(InboundStatus.PROCESSED)).isEqualTo(before);
    }

    @Test
    void temporaryFailureInItsOwnTransaction_isCountedDespiteOuterRollback() {
        UUID messageId = stored();
        double before = transitions(InboundStatus.RETRY);

        transactions.executeWithoutResult(status -> {
            inboundMessageService.recordTemporaryFailure(messageId, NEW, TIMEOUT);
            status.setRollbackOnly();
        });

        assertThat(transitions(InboundStatus.RETRY)).isEqualTo(before + 1);
    }

    private UUID stored() {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.MKB10, QUEUE, envelope("{\"id\":\"%s\"}".formatted(messageId)));
        return messageId;
    }

    private double transitions(InboundStatus status) {
        return registry.get(InboundTransitionMetrics.TRANSITIONS)
                .tag("stream", OneCStream.MKB10.name()).tag("status", status.name()).counter().count();
    }
}
