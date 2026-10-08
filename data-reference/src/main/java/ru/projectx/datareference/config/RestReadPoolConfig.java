package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Пул REST-чтений регистрируется бином, который никуда не внедряется: так контекст закрывает его раньше пула
 * приложения, а Boot публикует его метрики {@code hikaricp.*}. Здоровье базы проверяет только пул приложения: занятый
 * всплеском пул REST-чтений при живой базе не повод выводить экземпляр из работы и перекладывать нагрузку на соседей.
 */
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
