package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.onec.ReferenceImportService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.FixedClockConfig;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.TestJdbc;

/**
 * Срок хранения тела сообщения с персональными данными. Часы остановлены, поэтому «старое» сообщение кладётся
 * с временем приёма в прошлом: ждать сутки через {@code Thread.sleep} значит не проверять вовсе.
 */
@IntegrationTest
class PersonalDataRetentionTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_policies";

    @Autowired
    private PersonalDataRetention retention;

    @Autowired
    private ReferenceImportService importService;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private TestJdbc testJdbc;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void freshPolicyPayload_isKept() {
        UUID messageId = storePolicy(Duration.ZERO);

        retention.forgetExpiredPersonalData();

        assertThat(inboundMessageRepository.findById(messageId).orElseThrow().payload()).isNotNull();
    }

    /** Стирается только тело; сама строка остаётся — по ней видно, что сообщение приходило. */
    @Test
    void expiredPolicyPayload_isErasedButRowSurvives() {
        UUID messageId = storePolicy(Duration.ofDays(31));

        retention.forgetExpiredPersonalData();

        InboundMessage stored = inboundMessageRepository.findById(messageId).orElseThrow();
        assertThat(stored.payload()).isNull();
        assertThat(stored.receipt().stream()).isEqualTo(OneCStream.POLICIES);
        assertThat(stored.receipt().receivedAt()).isNotNull();
    }

    /**
     * В остальных потоках персональных данных нет, а тело справочного сообщения — то
     * единственное, чем строку можно переобработать, когда починили маппер.
     */
    @Test
    void expiredReferencePayload_isKept() {
        UUID messageId = storeRaw(OneCStream.MKB10, Duration.ofDays(400));

        retention.forgetExpiredPersonalData();

        assertThat(inboundMessageRepository.findById(messageId).orElseThrow().payload()).isNotNull();
    }

    /**
     * Строку со стёртым телом повторить нечем. Это должно быть внятным терминальным
     * отказом, а не обращением к пустой ссылке.
     */
    @Test
    void messageWithErasedPayload_failsWithClearReason() {
        UUID messageId = storePolicy(Duration.ofDays(31));
        retention.forgetExpiredPersonalData();
        InboundMessage erased = inboundMessageRepository.findById(messageId).orElseThrow();

        assertThatThrownBy(() -> importService.importMessage(erased))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageContaining("стёрто по сроку хранения");
    }

    private UUID storePolicy(Duration age) {
        return storeRaw(OneCStream.POLICIES, age);
    }

    /**
     * Кладёт сырьё и отодвигает время приёма в прошлое. Через сервис — чтобы строка была
     * настоящей; время правится запросом, потому что часы в тесте остановлены.
     */
    private UUID storeRaw(OneCStream stream, Duration age) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, stream, QUEUE, envelope("{\"id\":\"%s\"}".formatted(messageId)));
        testJdbc.update("update inbound_message set received_at = ? where message_id = ?",
                Timestamp.from(FixedClockConfig.NOW.minus(age)), messageId);
        return messageId;
    }
}
