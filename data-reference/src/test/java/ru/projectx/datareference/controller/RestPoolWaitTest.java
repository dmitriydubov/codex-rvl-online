package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.config.RestReadDataSource;
import ru.projectx.datareference.support.ExhaustedPool;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * У REST-чтений свой пул соединений. Когда его занял всплеск запросов, очередное чтение отвечает 503 через ожидание
 * соединения этого пула (2 с), а пул приёма и разбора остаётся свободным. И наоборот: занятый разбором пул
 * приложения REST-чтения не задевает. Лимит времени чтения ожидание соединения не ограничивает: он начинает
 * действовать только после того, как соединение получено.
 */
@IntegrationTest
class RestPoolWaitTest {

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(2);
    /** {@code lock_timeout} пула: занятый пул не должен отвечать медленнее чужой блокировки. */
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
    /** Свободный пул отдаёт соединение сразу; секунда — запас на медленную машину сборки. */
    private static final Duration IMMEDIATELY = Duration.ofSeconds(1);
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
    void restReadPoolExhausted_answers503AfterConnectionTimeout(String path) throws SQLException {
        try (ExhaustedPool ignored = ExhaustedPool.ofRestReads(dataSource)) {
            long started = System.nanoTime();

            MvcTestResult result = mvc.get().uri(path).exchange();

            Duration waited = Duration.ofNanos(System.nanoTime() - started);
            assertThat(result).hasStatus(503);
            assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(waited).isBetween(CONNECTION_TIMEOUT, LOCK_TIMEOUT);
        }
    }

    @Test
    void restReadPoolExhausted_leavesApplicationPoolFree() throws SQLException {
        try (ExhaustedPool ignored = ExhaustedPool.ofRestReads(dataSource)) {
            long started = System.nanoTime();

            try (Connection connection = applicationPool().getConnection()) {
                assertThat(connection.isValid(1)).isTrue();
            }

            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(IMMEDIATELY);
        }
    }

    /** Чтение доходит до базы: неизвестный полис — 404, а не 503 от занятого пула. */
    @Test
    void applicationPoolExhausted_restReadStillAnswers() throws SQLException {
        try (ExhaustedPool ignored = ExhaustedPool.of(dataSource)) {
            MvcTestResult result = mvc.get().uri("/internal/v1/policies/" + UNKNOWN_ID).exchange();

            assertThat(result).hasStatus(404);
        }
    }

    private DataSource applicationPool() throws SQLException {
        return dataSource.unwrap(RestReadDataSource.class).applicationPool();
    }
}
