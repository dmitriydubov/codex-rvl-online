package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.support.ExhaustedPool;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * REST-чтение, которому пул не дал соединения, отвечает 503 через ожидание соединения пула (2 с), а не через
 * умолчание HikariCP в 30 секунд. Лимит времени чтения тут не помогает: он начинает действовать только после
 * того, как соединение получено. Пул без свободных соединений — тот же путь, что недоступная база.
 */
@IntegrationTest
class RestPoolWaitTest {

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(2);
    /** {@code lock_timeout} пула: недоступная база не должна отвечать медленнее чужой блокировки. */
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final String UNKNOWN_ID = "f662d1c4-5b26-4248-8ba1-47954abd301b";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DataSource dataSource;

    @ParameterizedTest
    @ValueSource(strings = {
            "/internal/v1/programs/" + UNKNOWN_ID,
            "/internal/v1/policies/" + UNKNOWN_ID,
            "/internal/v1/mkb10?query=I25"})
    void noConnectionFromPool_answers503AfterConnectionTimeout(String path) throws SQLException {
        try (ExhaustedPool ignored = ExhaustedPool.of(dataSource)) {
            long started = System.nanoTime();

            MvcTestResult result = mvc.get().uri(path).exchange();

            Duration waited = Duration.ofNanos(System.nanoTime() - started);
            assertThat(result).hasStatus(503);
            assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(waited).isBetween(CONNECTION_TIMEOUT, LOCK_TIMEOUT);
        }
    }
}
