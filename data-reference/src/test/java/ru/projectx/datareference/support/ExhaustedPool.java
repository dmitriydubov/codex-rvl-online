package ru.projectx.datareference.support;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import ru.projectx.datareference.config.RestReadDataSource;

/**
 * Пул, у которого заняты все соединения: следующий запрос соединения ждёт его столько, сколько
 * разрешает настройка пула, и получает отказ. Для приложения это тот же путь, что и при недоступной базе:
 * свободного соединения нет, и пул отказывает по истечении своего ожидания. Соединения возвращаются в пул
 * при закрытии.
 *
 * <p>Пул занимается из потока теста, поэтому в этот момент соединений не должен держать никто другой: в тестах
 * с базой задачи по расписанию выключены, а слушатели брокера не подключены.
 */
public final class ExhaustedPool implements AutoCloseable {

    private final List<Connection> held;

    private ExhaustedPool(List<Connection> held) {
        this.held = held;
    }

    /**
     * Занимает все соединения пула приёма, разбора и задач по расписанию.
     *
     * @param dataSource источник соединений приложения
     * @return занятый пул; закрыть, чтобы вернуть соединения
     */
    public static ExhaustedPool of(DataSource dataSource) throws SQLException {
        return take(dataSource.unwrap(RestReadDataSource.class).applicationPool());
    }

    /**
     * Занимает все соединения пула REST-чтений — так выглядит всплеск запросов соседей.
     *
     * @param dataSource источник соединений приложения
     * @return занятый пул; закрыть, чтобы вернуть соединения
     */
    public static ExhaustedPool ofRestReads(DataSource dataSource) throws SQLException {
        return take(dataSource.unwrap(RestReadDataSource.class).restReadPool());
    }

    private static ExhaustedPool take(HikariDataSource pool) throws SQLException {
        List<Connection> held = new ArrayList<>();
        try {
            for (int taken = 0; taken < pool.getMaximumPoolSize(); taken++) {
                held.add(pool.getConnection());
            }
            return new ExhaustedPool(held);
        }
        catch (SQLException failure) {
            releaseAfterFailure(held, failure);
            throw failure;
        }
    }

    private static void releaseAfterFailure(List<Connection> held, SQLException failure) {
        try {
            new ExhaustedPool(held).close();
        }
        catch (SQLException releaseFailure) {
            failure.addSuppressed(releaseFailure);
        }
    }

    @Override
    public void close() throws SQLException {
        for (Connection connection : held) {
            connection.close();
        }
    }
}
