package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;

import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.ParseFailure;
import ru.projectx.datareference.service.ParseFailures;
import ru.projectx.datareference.support.ExhaustedPool;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Приём из 1С:УСК и разбор по расписанию при отказе пула в соединении — так выглядит недоступная база. Короткое
 * ожидание соединения меняет только то, как быстро приходит отказ, но не его смысл: для приёма это временный
 * отказ хранилища (пауза потребителей, сообщение остаётся в очереди), для разбора — временный отказ базы
 * (повтор позже), а проверка, ожила ли база, отвечает «нет» за секунды, задолго до следующей проверки.
 */
@IntegrationTest
class IngestPoolWaitTest {

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(2);
    /** Пауза между проверками базы во время паузы приёма: проверка обязана укладываться в неё с запасом. */
    private static final Duration STORAGE_CHECK_INTERVAL = Duration.ofSeconds(10);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private IngestStorageProbe probe;

    @Autowired
    private ParseFailures parseFailures;

    @Autowired
    private InboundMessageRepository rawMessages;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void noConnectionFromPool_storageCheckAnswersUnavailableWithinSeconds() throws SQLException {
        try (ExhaustedPool ignored = ExhaustedPool.of(dataSource)) {
            long started = System.nanoTime();

            boolean available = probe.storageAvailable();

            Duration waited = Duration.ofNanos(System.nanoTime() - started);
            assertThat(available).isFalse();
            assertThat(waited).isBetween(CONNECTION_TIMEOUT, STORAGE_CHECK_INTERVAL);
        }
    }

    @Test
    void noConnectionFromPool_isTemporaryForParsingAndStorageOutageForIngest() throws SQLException {
        RuntimeException failure;
        try (ExhaustedPool ignored = ExhaustedPool.of(dataSource)) {
            failure = catchRuntimeException(this::readRawMessageInTransaction);
        }

        assertThat(parseFailures.classify(failure)).isInstanceOf(ParseFailure.Temporary.class);
        assertThat(OneCStorageFailure.of(failure)).isInstanceOf(OneCStorageFailure.Outage.class);
    }

    private void readRawMessageInTransaction() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> rawMessages.existsById(UUID.randomUUID()));
    }
}
