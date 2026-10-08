package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Незакрытый пул оставил бы соединения висеть на стороне базы до их собственного тайм-аута, поэтому при остановке
 * закрываются оба пула. Контекст свой, потому что после теста он непригоден.
 */
@IntegrationTest
@TestPropertySource(properties = "spring.application.name=data-reference-pool-shutdown")
@DirtiesContext
class PoolShutdownTest {

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextClose_closesBothPools() throws SQLException {
        RestReadDataSource pools = dataSource.unwrap(RestReadDataSource.class);

        context.getBeanFactory().destroySingletons();

        assertThat(pools.applicationPool().isClosed()).as("пул приложения закрыт").isTrue();
        assertThat(pools.restReadPool().isClosed()).as("пул REST-чтений закрыт").isTrue();
    }
}
