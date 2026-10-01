package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.schedulers.ReferenceImportScheduler;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.TestJdbc;

/**
 * Текст SQL-ошибки не попадает в журнал: PostgreSQL кладёт в него Detail со значениями
 * отвергнутой строки, а через тот же путь записи идут полисы с персональными данными.
 *
 * <p>Ошибку даёт настоящая база: на время теста на справочник МКБ-10 ставится CHECK,
 * который отвергает один код, а в соседнем поле строки лежит синтетическое значение
 * на месте ПДн. Проход планировщика разбирает сообщение, вставка падает — и журнал
 * проверяется весь, что бы и кто бы в него ни написал: Hibernate, драйвер, Spring или сам
 * сервис. Тип отказа в журнале остаётся, иначе сбой нечем было бы диагностировать.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SqlErrorLogTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String CONSTRAINT = "ck_test_sql_error_log";
    private static final String REJECTED_CODE = "Z99.9";
    /** Синтетическое значение на месте ПДн — то, что Detail ошибки показал бы целиком. */
    private static final String PERSONAL_DATA = "Синтетикова Ксения Тестовна";
    private static final String FAILURE_TYPE = "org.springframework.dao.DataIntegrityViolationException";

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

    @BeforeEach
    void rejectOneCode() {
        databaseCleaner.clear();
        testJdbc.update("ALTER TABLE mkb10 ADD CONSTRAINT " + CONSTRAINT + " CHECK (code <> '" + REJECTED_CODE + "')");
    }

    @AfterEach
    void restoreSchema() {
        testJdbc.update("ALTER TABLE mkb10 DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
    }

    @Test
    void rejectedRow_leavesOnlyFailureTypeInLog(CapturedOutput log) {
        UUID messageId = store(mkb10(REJECTED_CODE, PERSONAL_DATA));

        scheduler.importPendingMessages();

        assertThat(lastErrorOf(messageId)).contains(CONSTRAINT, PERSONAL_DATA);
        assertThat(log.getAll())
                .contains("временная ошибка разбора сообщения " + messageId + " потока MKB10: " + FAILURE_TYPE)
                .doesNotContain(PERSONAL_DATA)
                .doesNotContain(CONSTRAINT)
                .doesNotContain("Failing row contains");
    }

    /** @return message-id строки сырья — по нему тест находит её исход */
    private UUID store(String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.MKB10, QUEUE, envelope(data));
        return messageId;
    }

    /** Полный текст ошибки остаётся в строке сырья — значит, ПДн в ошибке действительно были. */
    private String lastErrorOf(UUID messageId) {
        return inboundMessageRepository.findById(messageId).orElseThrow().lastError();
    }

    private static String mkb10(String code, String name) {
        return """
                {"id":"%s","code":"%s","name":"%s"}"""
                .formatted(UUID.randomUUID(), code, name);
    }
}
