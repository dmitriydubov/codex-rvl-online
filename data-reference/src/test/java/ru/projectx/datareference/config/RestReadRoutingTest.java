package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Пул REST-чтений получает только транзакцию «только чтение», начатую при обработке HTTP-запроса. Приём из очередей
 * и задачи по расписанию идут в пул приложения, даже когда их транзакция только читает: Spring Data открывает такую
 * транзакцию на каждый вызов репозитория вне своей транзакции.
 */
@ExtendWith(MockitoExtension.class)
class RestReadRoutingTest {

    @Mock
    private DataSource application;

    @Mock
    private DataSource restRead;

    @Mock
    private Connection applicationConnection;

    @Mock
    private Connection restReadConnection;

    private RestReadRouting routing;

    @BeforeEach
    void setUp() {
        routing = new RestReadRouting(application, restRead);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void readOnlyTransactionOfHttpRequest_goesToRestReadPool() throws SQLException {
        when(restRead.getConnection()).thenReturn(restReadConnection);
        inHttpRequest();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThat(routing.getConnection()).isSameAs(restReadConnection);
    }

    @Test
    void readOnlyTransactionOutsideHttpRequest_goesToApplicationPool() throws SQLException {
        when(application.getConnection()).thenReturn(applicationConnection);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThat(routing.getConnection()).isSameAs(applicationConnection);
    }

    /** Так ходит проверка здоровья базы: запрос к актуатору без транзакции. */
    @Test
    void httpRequestWithoutReadOnlyTransaction_goesToApplicationPool() throws SQLException {
        when(application.getConnection()).thenReturn(applicationConnection);
        inHttpRequest();

        assertThat(routing.getConnection()).isSameAs(applicationConnection);
    }

    @Test
    void neitherHttpRequestNorReadOnlyTransaction_goesToApplicationPool() throws SQLException {
        when(application.getConnection()).thenReturn(applicationConnection);

        assertThat(routing.getConnection()).isSameAs(applicationConnection);
    }

    private static void inHttpRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }
}
