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
 * Отдельный пул REST-чтений рядом с пулом приложения ({@link RestReadDataSource}) и проверка здоровья базы.
 *
 * <p>Пул REST-чтений — ещё и бин, который в другие бины не внедряется: так контекст закрывает его при остановке,
 * раньше пула приложения, а Spring Boot публикует его метрики {@code hikaricp.*} с меткой {@code pool="rest-read"}.
 * Проверка здоровья и метрики {@code jdbc.connections.*} такие бины не видят.
 *
 * <p>Здоровье базы ({@code db} в {@code /actuator/health}) проверяет только пул приложения: занятый всплеском
 * пул REST-чтений при живой базе — не повод выводить экземпляр из работы, это переложило бы нагрузку на соседей.
 * Собственная проверка Spring Boot нашла бы за источником маршрутизацию и проверяла бы оба пула.
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
