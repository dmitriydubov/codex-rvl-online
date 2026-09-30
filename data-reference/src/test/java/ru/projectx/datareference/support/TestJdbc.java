package ru.projectx.datareference.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Запрос к базе в обход модели — для того, что тест обязан подготовить, а приложение
 * делать не умеет и не должно.
 *
 * <p>Сегодня это одно: состарить строку сырья. Часы в тестах остановлены, сдвинуть их
 * вперёд нечем, а ждать сутки — не проверка. Заводить ради этого метод в боевом
 * репозитории нельзя: код, существующий только для теста, рано или поздно будет вызван
 * не из теста.
 */
public class TestJdbc {

    private final DataSource dataSource;

    public TestJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void update(String sql, Object... arguments) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            statement.executeUpdate();
        }
        catch (SQLException failed) {
            throw new IllegalStateException("не удалось выполнить подготовительный запрос: " + sql, failed);
        }
    }
}
