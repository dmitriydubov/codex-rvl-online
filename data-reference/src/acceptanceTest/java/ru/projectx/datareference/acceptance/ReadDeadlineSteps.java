package ru.projectx.datareference.acceptance;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import java.sql.SQLException;
import javax.sql.DataSource;

public class ReadDeadlineSteps {

    private final LateFirstReadDataSource dataSource;

    public ReadDeadlineSteps(DataSource dataSource) throws SQLException {
        this.dataSource = dataSource.unwrap(LateFirstReadDataSource.class);
    }

    @Дано("ответ базы Дата-референса на первое обращение следующего чтения приходит позже, чем истекает лимит чтения")
    public void firstResultOfNextReadArrivesAfterDeadline() {
        dataSource.delayFirstResultOfNextRead();
    }

    @After
    public void restore() {
        dataSource.restore();
    }
}
