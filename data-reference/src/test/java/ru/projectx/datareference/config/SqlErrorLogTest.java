package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.NestedExceptionUtils;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.SqlStates;
import ru.projectx.datareference.service.schedulers.ReferenceImportScheduler;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.TestJdbc;

/**
 * Текст SQL-ошибки не попадает в журнал: PostgreSQL кладёт в него Detail и Where со значениями
 * отвергнутой строки или тела, а через те же пути записи идут полисы с персональными данными.
 *
 * <p>Ошибки даёт настоящая база, на месте ПДн — синтетическое значение. Первая — при разборе:
 * на время теста на справочник МКБ-10 ставится CHECK, который отвергает один код. Вторая —
 * при записи сырья: тело, которое не принимает jsonb. Журнал проверяется весь, что бы и кто бы
 * в него ни написал: Hibernate, драйвер, Spring или сам сервис. Тип отказа и SQLSTATE в журнале
 * остаются, иначе сбой нечем было бы диагностировать. Значений нет и в причине отказа в строке
 * сырья, и в тексте исключения драйвера: драйверу запрещено дописывать в него подробности сервера.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SqlErrorLogTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String CONSTRAINT = "ck_test_sql_error_log";
    private static final String REJECTED_CODE = "Z99.9";
    /**
     * Синтетическое значение на месте ПДн. Короткое намеренно: Where PostgreSQL показывает
     * тело не целиком, а последние десятки символов перед ошибкой.
     */
    private static final String PERSONAL_DATA = "Ксения Тестовна";
    private static final String REJECTION = "база данных отклонила запись: DataIntegrityViolationException, SQLSTATE 23514";

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private TestJdbc testJdbc;

    @Autowired
    private ReferenceImportScheduler scheduler;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    /**
     * Ограничение снимается и перед установкой: оборванный прогон на постоянной базе
     * ({@code TEST_POSTGRES_URL}) иначе оставил бы его, и следующий упал бы на «already exists».
     */
    @BeforeEach
    void rejectOneCode() {
        databaseCleaner.clear();
        restoreSchema();
        testJdbc.update("ALTER TABLE mkb10 ADD CONSTRAINT " + CONSTRAINT + " CHECK (code <> '" + REJECTED_CODE + "')");
    }

    @AfterEach
    void restoreSchema() {
        testJdbc.update("ALTER TABLE mkb10 DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
    }

    /**
     * Нарушение CHECK при разборе: PostgreSQL печатает отвергнутую строку целиком (Detail). Повтор дал бы то же
     * самое, поэтому сообщение отклоняется окончательно; причина и журнал называют тип и SQLSTATE, но не строку.
     */
    @Test
    void rejectedRow_leavesOnlyFailureTypeAndSqlStateInLogAndReason(CapturedOutput log) {
        UUID messageId = store(mkb10(REJECTED_CODE, PERSONAL_DATA));

        scheduler.importPendingMessages();

        assertThat(lastErrorOf(messageId)).isEqualTo(REJECTION);
        assertThat(log.getAll())
                .contains("сообщение " + messageId + " потока MKB10 отклонено окончательно на попытке 1: "
                        + "DataIntegrityViolationException, SQLSTATE 23514")
                .doesNotContain(PERSONAL_DATA)
                .doesNotContain(CONSTRAINT)
                .doesNotContain("Failing row contains");
    }

    /**
     * Тело, которое jsonb не принимает ({@code \u0000} в строке), при записи сырья: PostgreSQL
     * печатает в Where тело до места ошибки — со всем, что в нём стояло раньше. Драйвер этих подробностей
     * в текст исключения не берёт, поэтому значения нет ни в исключении, ни в журнале, а код ошибки остаётся.
     */
    @Test
    void rejectedPayload_leavesNoPayloadTextInExceptionOrLog(CapturedOutput log) {
        String poisonedName = PERSONAL_DATA + "\\u0000";

        Throwable failure = catchThrowable(() -> store(mkb10("A00", poisonedName)));

        assertThat(SqlStates.deepest(failure)).hasValue("22P05");
        assertThat(NestedExceptionUtils.getMostSpecificCause(failure)).hasMessageNotContaining(PERSONAL_DATA);
        assertThat(log.getAll())
                .doesNotContain(PERSONAL_DATA)
                .doesNotContain("JSON data");
    }

    /** @return message-id строки сырья — по нему тест находит её исход */
    private UUID store(String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.MKB10, QUEUE, envelope(data));
        return messageId;
    }

    /** Причина отказа в строке сырья. */
    private String lastErrorOf(UUID messageId) {
        return inboundMessageRepository.findById(messageId).orElseThrow().outcome().lastError();
    }

    private static String mkb10(String code, String name) {
        return """
                {"id":"%s","code":"%s","name":"%s"}"""
                .formatted(UUID.randomUUID(), code, name);
    }
}
