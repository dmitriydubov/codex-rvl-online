package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class SessionTimeoutsPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SessionTimeoutsPostProcessor.class);

    private final ObjectProvider<DatabaseSessionTimeouts> timeouts;

    public SessionTimeoutsPostProcessor(ObjectProvider<DatabaseSessionTimeouts> timeouts) {
        this.timeouts = timeouts;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        BootPool.of(bean, beanName).ifPresent(this::setSessionTimeouts);
        return bean;
    }

    private void setSessionTimeouts(HikariDataSource application) {
        if (SessionStartupOptions.isSet(application)) {
            log.warn("стартовый параметр options пула {} из spring.datasource.hikari заменён таймаутами сеанса "
                    + "projectx.database.session-timeouts", application.getPoolName());
        }
        SessionStartupOptions.set(application, timeouts.getObject());
    }
}
