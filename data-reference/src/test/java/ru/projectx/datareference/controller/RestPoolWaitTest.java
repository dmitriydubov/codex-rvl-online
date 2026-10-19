package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.config.restread.RestReadDataSource;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.support.ExhaustedPool;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class RestPoolWaitTest {

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration IMMEDIATELY = Duration.ofSeconds(1);
    private static final String UNKNOWN_ID = "f662d1c4-5b26-4248-8ba1-47954abd301b";
    private static final UUID UNKNOWN_MESSAGE = UUID.fromString(UNKNOWN_ID);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private InboundMessageRepository rawMessages;

    @Autowired
    private PlatformTransactionManager transactions;

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

    @Test
    void applicationPoolExhausted_restReadStillAnswers() throws SQLException {
        try (ExhaustedPool ignored = ExhaustedPool.of(dataSource)) {
            MvcTestResult result = mvc.get().uri("/internal/v1/policies/" + UNKNOWN_ID).exchange();

            assertThat(result).hasStatus(404);
        }
    }

    @Test
    void restReadPoolExhausted_repositoryReadOutsideHttpRequestUsesApplicationPool() throws Exception {
        try (ExhaustedPool ignored = ExhaustedPool.ofRestReads(dataSource)) {
            long started = System.nanoTime();

            boolean found = outsideHttpRequest(() -> rawMessages.existsById(UNKNOWN_MESSAGE));

            assertThat(found).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(IMMEDIATELY);
        }
    }

    @Test
    void restReadPoolExhausted_readOnlyTransactionOutsideHttpRequestUsesApplicationPool() throws Exception {
        TransactionTemplate readOnly = new TransactionTemplate(transactions);
        readOnly.setReadOnly(true);
        try (ExhaustedPool ignored = ExhaustedPool.ofRestReads(dataSource)) {
            long started = System.nanoTime();

            Long messages = outsideHttpRequest(() -> readOnly.execute(status -> rawMessages.count()));

            assertThat(messages).isNotNull();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(IMMEDIATELY);
        }
    }

    private static <T> T outsideHttpRequest(Callable<T> read) throws Exception {
        try (ExecutorService thread = Executors.newSingleThreadExecutor()) {
            return thread.submit(read).get(LOCK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
    }

    private DataSource applicationPool() throws SQLException {
        return dataSource.unwrap(RestReadDataSource.class).applicationPool();
    }
}
