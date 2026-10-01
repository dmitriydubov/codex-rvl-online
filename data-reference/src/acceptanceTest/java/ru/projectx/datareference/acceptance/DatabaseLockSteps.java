package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;

/**
 * Долгая операция в базе Дата-референса: другой сеанс держит таблицу под ACCESS EXCLUSIVE —
 * блокировкой, которую ждёт даже обычный SELECT (PostgreSQL 16, «Explicit Locking»). Так
 * выглядят ручной SQL или долгая миграция на живой базе: база жива, но вовремя не отвечает.
 *
 * <p>Блокировка живёт в открытой транзакции отдельного соединения и снимается откатом —
 * шагом «завершилась» или после сценария, чтобы следующий сценарий начинал с доступной базой.
 * Имя данных из сценария переводится в таблицу явным перечнем: неизвестное имя роняет шаг,
 * а не блокирует «что-нибудь».
 */
public class DatabaseLockSteps {

    private static final Map<String, String> TABLES = Map.of(
            "полисы", "policies",
            "договоры страхования", "insurance_contracts");

    private final DataSource dataSource;
    private @Nullable Connection lockingSession;

    public DatabaseLockSteps(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Дано("данные {string} в базе Дата-референса заблокированы долгой операцией")
    public void dataLocked(String data) throws SQLException {
        assertThat(TABLES).as("данные, которые умеет блокировать шаг").containsKey(data);
        Connection session = dataSource.getConnection();
        lockingSession = session;
        session.setAutoCommit(false);
        try (Statement statement = session.createStatement()) {
            statement.execute("lock table " + TABLES.get(data) + " in access exclusive mode");
        }
    }

    @Когда("долгая операция в базе Дата-референса завершилась")
    public void operationFinished() throws SQLException {
        assertThat(lockingSession).as("блокировку взял шаг «заблокированы долгой операцией»").isNotNull();
        release();
    }

    @After
    public void release() throws SQLException {
        Optional<Connection> session = Optional.ofNullable(lockingSession);
        lockingSession = null;
        if (session.isPresent()) {
            try (Connection connection = session.get()) {
                connection.rollback();
            }
        }
    }
}
