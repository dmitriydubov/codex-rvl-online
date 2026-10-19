package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.repository.inbound.InboxRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class OneCIngestServiceTransactionTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String PAYLOAD = "{\"data\":{\"code\":\"A00\"},\"metadata\":{}}";

    private static final String UNSTORABLE_PAYLOAD = "это не JSON";

    private static final String NOT_JSON = "22P02";

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private OneCIngestService ingestService;

    @Autowired
    private InboxRepository inboxRepository;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void rawNotStored_leavesNoInboxMark() {
        UUID messageId = UUID.randomUUID();

        assertThatRuntimeException().isThrownBy(
                        () -> ingestService.receive(messageId, OneCStream.MKB10, QUEUE, UNSTORABLE_PAYLOAD))
                .satisfies(failure -> assertThat(sqlStatesOf(failure)).contains(NOT_JSON));

        assertThat(inboxRepository.findById(messageId)).isEmpty();
        assertThat(inboundMessageRepository.findById(messageId)).isEmpty();
    }

    @Test
    void redeliveryAfterFailedStorage_isStoredAsNew() {
        UUID messageId = UUID.randomUUID();
        assertThatRuntimeException().isThrownBy(
                        () -> ingestService.receive(messageId, OneCStream.MKB10, QUEUE, UNSTORABLE_PAYLOAD))
                .satisfies(failure -> assertThat(sqlStatesOf(failure)).contains(NOT_JSON));

        boolean stored = ingestService.receive(messageId, OneCStream.MKB10, QUEUE, PAYLOAD);

        assertThat(stored).isTrue();
        assertThat(inboundMessageRepository.findById(messageId)).isPresent();
        assertThat(inboxRepository.findById(messageId)).isPresent();
    }

    private static List<String> sqlStatesOf(Throwable failure) {
        return Stream.iterate(failure, Objects::nonNull, Throwable::getCause)
                .filter(SQLException.class::isInstance)
                .map(cause -> ((SQLException) cause).getSQLState())
                .toList();
    }
}
