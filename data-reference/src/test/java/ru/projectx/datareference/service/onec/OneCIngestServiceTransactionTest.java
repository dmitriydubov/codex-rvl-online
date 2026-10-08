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
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.InboxRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Защищает общую транзакцию отметки во входящих и записи сырья на настоящем PostgreSQL. Unit-тест этого не видит:
 * у каждой записи своя {@code @Transactional}, и без общей отметка коммитилась бы раньше сырья. Сырьё здесь роняет
 * колонка {@code jsonb} при коммите, когда отметка уже сделана, то есть ровно на границе транзакции.
 */
@IntegrationTest
class OneCIngestServiceTransactionTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String PAYLOAD = "{\"data\":{\"code\":\"A00\"},\"metadata\":{}}";

    /** Тело, которое колонка {@code jsonb} не примет. */
    private static final String UNSTORABLE_PAYLOAD = "это не JSON";

    /**
     * SQLSTATE отказа PostgreSQL на тексте, который не JSON: {@code invalid_text_representation}
     * (PostgreSQL 16, приложение A «Коды ошибок»). Иначе тест принял бы и отказ, случившийся
     * до отметки во входящих.
     */
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

    /**
     * Отмеченное, но не сохранённое сообщение потеряно навсегда: брокер отдаст его снова,
     * а дедупликация уже сочтёт его известным. Поэтому не легло сырьё — нет и отметки.
     */
    @Test
    void rawNotStored_leavesNoInboxMark() {
        UUID messageId = UUID.randomUUID();

        assertThatRuntimeException().isThrownBy(
                        () -> ingestService.receive(messageId, OneCStream.MKB10, QUEUE, UNSTORABLE_PAYLOAD))
                .satisfies(failure -> assertThat(sqlStatesOf(failure)).contains(NOT_JSON));

        assertThat(inboxRepository.findById(messageId)).isEmpty();
        assertThat(inboundMessageRepository.findById(messageId)).isEmpty();
    }

    /** То же следствием: передоставка после отказа принимается как новое сообщение, а не как дубль. */
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
