package ru.projectx.datareference.config.restread;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import ru.projectx.datareference.config.BootPool;

public class RestReadDataSourcePostProcessor implements BeanPostProcessor, Ordered {

    private final BeanFactory beans;

    public RestReadDataSourcePostProcessor(BeanFactory beans) {
        this.beans = beans;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        return BootPool.of(bean, beanName).<Object>map(this::withRestReadPool).orElse(bean);
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    private RestReadDataSource withRestReadPool(HikariDataSource application) {
        RestReadPoolProperties settings = beans.getBean(RestReadPoolProperties.class);
        Environment environment = beans.getBean(Environment.class);
        return new RestReadDataSource(application, RestReadPools.restReadPool(application, environment, settings));
    }
}
