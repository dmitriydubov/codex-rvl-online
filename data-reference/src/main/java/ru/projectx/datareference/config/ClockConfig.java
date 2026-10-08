package ru.projectx.datareference.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Единый источник времени сервиса. Его внедряют вместо вызова {@code Instant.now()}, чтобы тесты подменяли часы на
 * {@link Clock#fixed}. По той же причине в сущностях нет {@code @CreationTimestamp} и {@code @UpdateTimestamp}:
 * Hibernate ставит их по системным часам JVM мимо этого бина.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** UTC, а не системная зона: контейнер и машина разработчика живут в разных поясах. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
