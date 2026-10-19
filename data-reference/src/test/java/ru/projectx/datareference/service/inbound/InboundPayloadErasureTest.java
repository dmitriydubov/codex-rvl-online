package ru.projectx.datareference.service.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionOperations;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.FixedClockConfig;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.TestJdbc;

@IntegrationTest
class InboundPayloadErasureTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_contracts-changes";

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository repository;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private TestJdbc testJdbc;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void outcomeOfMessageReadBeforeErasure_doesNotWriteTheBodyBack() {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.INSURANCE_CONTRACTS, QUEUE, envelope("{\"contractId\":\"1\"}"));

        transactions.executeWithoutResult(status -> {
            InboundMessage readBeforeErasure = repository.findById(messageId).orElseThrow();
            testJdbc.update("update inbound_message set payload = null where message_id = ?", messageId);
            readBeforeErasure.markProcessed(FixedClockConfig.NOW);
        });

        InboundMessage saved = repository.findById(messageId).orElseThrow();
        assertThat(saved.payload()).isNull();
        assertThat(saved.outcome().status()).isEqualTo(InboundStatus.PROCESSED);
    }
}
