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
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.inbound.InboundMessageService;
import ru.projectx.datareference.service.database.SqlStates;
import ru.projectx.datareference.service.schedulers.ReferenceImportScheduler;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.TestJdbc;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SqlErrorLogTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String CONSTRAINT = "ck_test_sql_error_log";
    private static final String REJECTED_CODE = "Z99.9";
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

    @Test
    void rejectedPayload_leavesNoPayloadTextInExceptionOrLog(CapturedOutput log) {
        String poisonedName = PERSONAL_DATA + "\\u0000";

        Throwable failure = catchThrowable(() -> store(mkb10("A00", poisonedName)));

        assertThat(SqlStates.deepest(failure)).hasValue("22P05");
        assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                .hasMessageContaining("unsupported Unicode escape sequence")
                .hasMessageNotContaining(PERSONAL_DATA);
        assertThat(log.getAll())
                .doesNotContain(PERSONAL_DATA)
                .doesNotContain("JSON data");
    }

    private UUID store(String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.MKB10, QUEUE, envelope(data));
        return messageId;
    }

    private String lastErrorOf(UUID messageId) {
        return inboundMessageRepository.findById(messageId).orElseThrow().outcome().lastError();
    }

    private static String mkb10(String code, String name) {
        return """
                {"id":"%s","code":"%s","name":"%s"}"""
                .formatted(UUID.randomUUID(), code, name);
    }
}
