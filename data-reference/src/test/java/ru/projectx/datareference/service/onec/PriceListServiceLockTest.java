package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.SqlStates;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.TestJdbc;

@IntegrationTest
class PriceListServiceLockTest {

    private static final UUID SERVICE = UUID.fromString("2a6f0c3e-9b18-4d57-8e41-c5d2a7f9b063");
    private static final UUID APPLIED_CID = UUID.fromString("8e4fab21-5c3d-4a9f-8b72-1d6c9f3a5e28");
    private static final UUID CID = UUID.fromString("7d3e9a10-4b2c-4f8e-9a61-0c5b8e2f4d17");
    private static final String QUEUE = "1c-usk_to_rvl-online_pricelists-contragents-changes";
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    @Autowired
    private ReferenceImportService importService;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository inbound;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private TestJdbc testJdbc;

    @BeforeEach
    void appliedTransferOfService() {
        databaseCleaner.clear();
        UUID appliedFinal = UUID.randomUUID();
        testJdbc.update("""
                insert into inbound_message (message_id, stream, source_queue, payload, status, attempt, received_at,
                                             correlation_id, batch_count, row_count)
                values (?, 'PRICE_LISTS', ?, cast('{}' as jsonb), 'PROCESSED', 0, ?, ?, 0, 0)
                """, appliedFinal, QUEUE, Timestamp.from(Instant.parse("2026-03-14T09:00:00Z")), APPLIED_CID);
        testJdbc.update("insert into price_list_finalization (service_external_id, final_message_id) values (?, ?)",
                SERVICE, appliedFinal);
    }

    @Test
    void partOfService_waitsForServiceRowHeldElsewhere_andFailsTemporarily() throws SQLException {
        InboundMessage batch = inboundMessageService.store(UUID.randomUUID(), OneCStream.PRICE_LISTS, QUEUE,
                envelope("{\"cid\":\"%s\",\"keyValue\":\"%s\",\"recordSet\":[]}".formatted(CID, SERVICE)));

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(
                    "select 1 from price_list_finalization where service_external_id = ? for no key update")) {
                lock.setObject(1, SERVICE);
                lock.executeQuery();
            }

            assertThatThrownBy(() -> importService.importMessage(batch))
                    .isInstanceOf(PessimisticLockingFailureException.class)
                    .satisfies(failure -> assertThat(SqlStates.deepest(failure)).contains(LOCK_NOT_AVAILABLE));
            holder.rollback();
        }

        assertThat(inbound.findById(batch.receipt().messageId()))
                .hasValueSatisfying(stored -> assertThat(stored.outcome().status()).isEqualTo(InboundStatus.NEW));
    }

    @Test
    void partOfService_isBuffered_whenServiceRowIsFree() {
        InboundMessage batch = inboundMessageService.store(UUID.randomUUID(), OneCStream.PRICE_LISTS, QUEUE,
                envelope("{\"cid\":\"%s\",\"keyValue\":\"%s\",\"recordSet\":[]}".formatted(CID, SERVICE)));

        importService.importMessage(batch);

        assertThat(inbound.findById(batch.receipt().messageId()))
                .hasValueSatisfying(stored -> assertThat(stored.outcome().status()).isEqualTo(InboundStatus.BUFFERED));
    }
}
