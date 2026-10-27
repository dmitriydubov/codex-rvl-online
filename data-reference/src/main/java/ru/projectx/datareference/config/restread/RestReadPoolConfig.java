package ru.projectx.datareference.config.restread;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RestReadPoolConfig {

    public static final String REST_READ_POOL = "restReadPool";

    @Bean
    static BeanPostProcessor restReadDataSourcePostProcessor(BeanFactory beans) {
        return new RestReadDataSourcePostProcessor(beans);
    }

    @Bean(name = REST_READ_POOL, defaultCandidate = false, destroyMethod = "close")
    HikariDataSource restReadPool(DataSource dataSource) throws SQLException {
        return dataSource.unwrap(RestReadDataSource.class).restReadPool();
    }

    @Bean
    ApplicationPool applicationPool(DataSource dataSource) throws SQLException {
        return new ApplicationPool(dataSource.unwrap(RestReadDataSource.class).applicationPool());
    }

    @Bean
    HealthContributor dbHealthContributor(ApplicationPool application) {
        return new DataSourceHealthIndicator(application.pool());
    }
}
