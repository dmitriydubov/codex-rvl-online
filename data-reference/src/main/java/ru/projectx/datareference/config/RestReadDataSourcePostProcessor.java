package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

public class RestReadDataSourcePostProcessor implements BeanPostProcessor, Ordered {

    static final String BOOT_POOL = "dataSource";

    private final BeanFactory beans;

    public RestReadDataSourcePostProcessor(BeanFactory beans) {
        this.beans = beans;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof HikariDataSource application && BOOT_POOL.equals(beanName)) {
            RestReadPoolProperties settings = beans.getBean(RestReadPoolProperties.class);
            Environment environment = beans.getBean(Environment.class);
            return new RestReadDataSource(application, RestReadPools.restReadPool(application, environment, settings));
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
