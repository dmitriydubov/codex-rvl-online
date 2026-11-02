package ru.projectx.datareference.config.migration;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class MigrationLockPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof SpringLiquibase liquibase && !(liquibase.getDataSource() instanceof MigrationLockDataSource)) {
            liquibase.setDataSource(new MigrationLockDataSource(liquibase.getDataSource()));
        }
        return bean;
    }
}
