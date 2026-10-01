package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.InboxRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Отметка во входящих и сырьё — одна транзакция, на настоящем PostgreSQL. Unit-тест этого
 * не видит: у каждой из двух записей своя {@code @Transactional}, и без общей отметка
 * коммитилась бы раньше, чем сырьё попытается лечь в базу.
 *
 * <p>Сырьё здесь роняет сама колонка: {@code jsonb} не принимает текст, который не JSON.
 * Отказ случается при коммите, когда отметка уже сделана, — то есть ровно на границе
 * транзакции, которую тест и проверяет.
 */
@IntegrationTest
class OneCIngestServiceTransactionTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String PAYLOAD = "{\"data\":{\"code\":\"A00\"},\"metadata\":{}}";

    /** Тело, которое колонка {@code jsonb} не примет. */
    private static final String UNSTORABLE_PAYLOAD = "это не JSON";

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
                () -> ingestService.receive(messageId, OneCStream.MKB10, QUEUE, UNSTORABLE_PAYLOAD));

        assertThat(inboxRepository.findById(messageId)).isEmpty();
        assertThat(inboundMessageRepository.findById(messageId)).isEmpty();
    }

    /** То же следствием: передоставка после отказа принимается как новое сообщение, а не как дубль. */
    @Test
    void redeliveryAfterFailedStorage_isStoredAsNew() {
        UUID messageId = UUID.randomUUID();
        assertThatRuntimeException().isThrownBy(
                () -> ingestService.receive(messageId, OneCStream.MKB10, QUEUE, UNSTORABLE_PAYLOAD));

        boolean stored = ingestService.receive(messageId, OneCStream.MKB10, QUEUE, PAYLOAD);

        assertThat(stored).isTrue();
        assertThat(inboundMessageRepository.findById(messageId)).isPresent();
        assertThat(inboxRepository.findById(messageId)).isPresent();
    }
}
