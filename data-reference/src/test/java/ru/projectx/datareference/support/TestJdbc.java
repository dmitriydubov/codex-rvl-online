package ru.projectx.datareference.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

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
