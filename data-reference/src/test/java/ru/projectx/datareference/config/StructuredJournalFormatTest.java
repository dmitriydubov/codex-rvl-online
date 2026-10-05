package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import ru.projectx.datareference.support.StructuredJournal;
import tools.jackson.databind.JsonNode;

/**
 * Журнал сервиса пишется в формате Elastic Common Schema (ECS): каждая строка — объект JSON, и ключи MDC
 * становятся его полями.
 *
 * <p>Сборщик журналов контура разбирает записи по полям, а не по тексту. По полям {@code messageId},
 * {@code stream}, {@code queue} и {@code requestId} находится вся история сообщения 1С или запроса. В простом
 * текстовом формате Spring Boot этих ключей в строке не было вовсе.
 */
@ExtendWith(OutputCaptureExtension.class)
class StructuredJournalFormatTest {

    private static final Logger log = LoggerFactory.getLogger(StructuredJournalFormatTest.class);

    private static final String MDC_KEY = "messageId";
    private static final String MDC_VALUE = "9f1c0f2e-0000-4000-8000-00000000000c";

    private StructuredJournal journal;

    @BeforeEach
    void startJournal() {
        journal = StructuredJournal.start();
    }

    @AfterEach
    void stopJournal() {
        journal.close();
        MDC.remove(MDC_KEY);
    }

    /** Строки запуска тоже JSON: заставка Spring Boot в консоль не выводится и разбор журнала не ломает. */
    @Test
    void everyLine_isEcsJsonRecord(CapturedOutput output) {
        log.info("проверка формата");

        assertThat(StructuredJournal.records(output)).allSatisfy(record -> {
            assertThat(record.path("ecs").path("version").asString()).isEqualTo("8.11");
            assertThat(record.path("service").path("name").asString()).isEqualTo("data-reference");
            assertThat(record.path("log").path("level").isString()).isTrue();
        });
    }

    @Test
    void record_carriesLevelLoggerAndMessage(CapturedOutput output) {
        log.warn("проверка формата");

        JsonNode record = StructuredJournal.recordWith(output, "проверка формата");

        assertThat(record.path("log").path("level").asString()).isEqualTo("WARN");
        assertThat(record.path("log").path("logger").asString()).isEqualTo(StructuredJournalFormatTest.class.getName());
        assertThat(record.path("message").asString()).isEqualTo("проверка формата");
    }

    @Test
    void mdcKey_becomesFieldOfRecord(CapturedOutput output) {
        MDC.put(MDC_KEY, MDC_VALUE);

        log.info("проверка формата");

        assertThat(StructuredJournal.recordWith(output, "проверка формата").path(MDC_KEY).asString())
                .isEqualTo(MDC_VALUE);
    }
}
