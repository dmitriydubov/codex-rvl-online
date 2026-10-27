package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class SessionTimeoutsPostProcessor implements BeanPostProcessor {

    static final String BOOT_POOL = "dataSource";

    private final ObjectProvider<DatabaseSessionTimeouts> timeouts;

    public SessionTimeoutsPostProcessor(ObjectProvider<DatabaseSessionTimeouts> timeouts) {
        this.timeouts = timeouts;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof HikariDataSource application && BOOT_POOL.equals(beanName)) {
            SessionStartupOptions.set(application, timeouts.getObject());
            return application;
        }
        return bean;
    }
}
