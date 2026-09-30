package ru.projectx.datareference.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Источник времени для всего сервиса.
 *
 * <p>Везде, где нужно «сейчас», инъецируется этот {@link Clock}, а не вызывается
 * {@code Instant.now()}. Ради тестов: подменив бин на {@link Clock#fixed}, они
 * проверяют сроки и пороги без {@code Thread.sleep}.
 *
 * <p>По той же причине в сущностях нет {@code @CreationTimestamp} и
 * {@code @UpdateTimestamp}: аннотации Hibernate этот бин не видят и штампуют момент
 * из системных часов JVM в обход Spring.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** UTC, а не системная зона: контейнер и разработческая машина живут в разных поясах. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
