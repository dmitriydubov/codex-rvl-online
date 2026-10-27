package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;

public final class BootPool {

    public static final String BEAN_NAME = "dataSource";

    private BootPool() {
    }

    public static Optional<HikariDataSource> of(Object bean, String beanName) {
        if (bean instanceof HikariDataSource pool && BEAN_NAME.equals(beanName)) {
            return Optional.of(pool);
        }
        return Optional.empty();
    }
}
