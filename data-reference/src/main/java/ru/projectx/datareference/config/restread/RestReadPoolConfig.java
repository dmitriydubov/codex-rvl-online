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

    @Bean
    static BeanPostProcessor restReadDataSourcePostProcessor(BeanFactory beans) {
        return new RestReadDataSourcePostProcessor(beans);
    }

    @Bean(defaultCandidate = false, destroyMethod = "close")
    HikariDataSource restReadPool(DataSource dataSource) throws SQLException {
        return dataSource.unwrap(RestReadDataSource.class).restReadPool();
    }

    @Bean
    HealthContributor dbHealthContributor(DataSource dataSource) throws SQLException {
        return new DataSourceHealthIndicator(dataSource.unwrap(RestReadDataSource.class).applicationPool());
    }
}
