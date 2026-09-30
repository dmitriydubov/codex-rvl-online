package ru.projectx.datareference.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Часы с остановленной стрелкой: время в тестах — не источник случайности.
 *
 * <p>{@code @Primary}, а не подмена бина по имени: инъекция {@link Clock} идёт по типу,
 * и приоритетный кандидат её выигрывает — включать переопределение определений бинов
 * ради этого не нужно.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfig {

    public static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
