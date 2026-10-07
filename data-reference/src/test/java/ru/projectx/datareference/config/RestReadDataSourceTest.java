package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Источник соединений приложения держит оба пула и отдаёт их по отдельности (здоровье базы, метрики, тесты). Сам
 * он соединений не открывает, пока они не нужны.
 */
@ExtendWith(MockitoExtension.class)
class RestReadDataSourceTest {

    @Mock
    private HikariDataSource application;

    @Mock
    private HikariDataSource restRead;

    @Test
    void givesBothPools() {
        RestReadDataSource dataSource = new RestReadDataSource(application, restRead);

        assertThat(dataSource.applicationPool()).isSameAs(application);
        assertThat(dataSource.restReadPool()).isSameAs(restRead);
    }

    @Test
    void creation_opensNoConnection() {
        new RestReadDataSource(application, restRead);

        verifyNoInteractions(application, restRead);
    }

    @Test
    void unwrap_findsItself() throws SQLException {
        RestReadDataSource dataSource = new RestReadDataSource(application, restRead);

        assertThat(dataSource.unwrap(RestReadDataSource.class)).isSameAs(dataSource);
    }
}
