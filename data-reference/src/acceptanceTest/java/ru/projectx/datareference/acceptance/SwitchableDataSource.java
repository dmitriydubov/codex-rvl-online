package ru.projectx.datareference.acceptance;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Optional;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Пул соединений сервиса с выключателем: в состоянии «база недоступна» каждый запрос соединения отказывает
 * ошибкой соединения с заданным SQLSTATE — так же, как пул HikariCP, когда базы нет (он бросает
 * {@code SQLTransientConnectionException} с кодом последней ошибки соединения). Остановить саму базу
 * сценарий не может: она одна на всю приёмку.
 *
 * <p>Это стенд, а не код сервиса: сервис видит обычный {@link DataSource}.
 */
final class SwitchableDataSource extends DelegatingDataSource {

    private volatile @Nullable String failingSqlState;

    SwitchableDataSource(DataSource pool) {
        super(pool);
    }

    /** С этого момента каждое соединение отказывает с {@code sqlState}. */
    void failConnectionsWith(String sqlState) {
        failingSqlState = sqlState;
    }

    /** База снова принимает соединения. */
    void restore() {
        failingSqlState = null;
    }

    @Override
    public Connection getConnection() throws SQLException {
        failIfUnavailable();
        return super.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        failIfUnavailable();
        return super.getConnection(username, password);
    }

    private void failIfUnavailable() throws SQLException {
        Optional<String> sqlState = Optional.ofNullable(failingSqlState);
        if (sqlState.isPresent()) {
            throw new SQLTransientConnectionException("стенд приёмки: база недоступна", sqlState.get());
        }
    }
}
