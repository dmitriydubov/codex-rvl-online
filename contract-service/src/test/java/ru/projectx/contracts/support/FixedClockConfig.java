package ru.projectx.contracts.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfig {

    public static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
