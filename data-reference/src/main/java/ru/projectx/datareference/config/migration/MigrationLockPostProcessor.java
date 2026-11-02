package ru.projectx.datareference.config.migration;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class MigrationLockPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof SpringLiquibase liquibase && !locked(liquibase.getDataSource())) {
            liquibase.setDataSource(new MigrationLockDataSource(liquibase.getDataSource()));
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SpringLiquibase liquibase) {
            liquibase.setDataSource(withoutMigrationLock(liquibase.getDataSource()));
        }
        return bean;
    }

    private static boolean locked(DataSource dataSource) {
        return dataSource instanceof MigrationLockDataSource;
    }

    private static DataSource withoutMigrationLock(DataSource dataSource) {
        return locked(dataSource) ? ((MigrationLockDataSource) dataSource).getTargetDataSource() : dataSource;
    }
}
