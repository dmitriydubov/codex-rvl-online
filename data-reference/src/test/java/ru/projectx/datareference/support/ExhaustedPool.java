package ru.projectx.datareference.support;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Пул приложения, у которого заняты все соединения: следующий запрос соединения ждёт его столько, сколько
 * разрешает настройка пула, и получает отказ. Для приложения это тот же путь, что и при недоступной базе:
 * свободного соединения нет, и пул отказывает по истечении своего ожидания. Соединения возвращаются в пул
 * при закрытии.
 */
public final class ExhaustedPool implements AutoCloseable {

    private final List<Connection> held;

    private ExhaustedPool(List<Connection> held) {
        this.held = held;
    }

    /**
     * Занимает все соединения пула.
     *
     * @param dataSource пул приложения
     * @return занятый пул; закрыть, чтобы вернуть соединения
     */
    public static ExhaustedPool of(DataSource dataSource) throws SQLException {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        List<Connection> held = new ArrayList<>();
        try {
            for (int taken = 0; taken < pool.getMaximumPoolSize(); taken++) {
                held.add(pool.getConnection());
            }
            return new ExhaustedPool(held);
        }
        catch (SQLException failure) {
            new ExhaustedPool(held).close();
            throw failure;
        }
    }

    @Override
    public void close() throws SQLException {
        for (Connection connection : held) {
            connection.close();
        }
    }
}
