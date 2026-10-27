package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

class BootPoolTest {

    @Test
    void hikariPoolNamedDataSource_isTheBootPool() {
        HikariDataSource application = new HikariDataSource();

        assertThat(BootPool.of(application, "dataSource")).containsSame(application);
    }

    @Test
    void hikariPoolUnderOtherName_isNotTheBootPool() {
        assertThat(BootPool.of(new HikariDataSource(), "restReadPool")).isEmpty();
    }

    @Test
    void otherBeanNamedDataSource_isNotTheBootPool() {
        assertThat(BootPool.of(new Object(), "dataSource")).isEmpty();
    }
}
