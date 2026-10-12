package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.cache.interceptor.SimpleKey;
import ru.projectx.datareference.support.TestApiProperties;

class TodayKeyGeneratorTest {

    private static final UUID LPU = UUID.fromString("b515b8fc-d13c-4b0b-aae3-33e0ca86ff1c");
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 11, 2);

    @Test
    void keyIsTodayInMoscowFollowedByArguments() throws NoSuchMethodException {
        TodayKeyGenerator generator = generatorAt("2026-12-31T21:30:00Z");

        Object key = generator.generate(this, anyMethod(), LPU, CHECK_IN);

        assertThat(key).isEqualTo(new SimpleKey(LocalDate.of(2027, 1, 1), LPU, CHECK_IN));
    }

    @Test
    void sameArgumentsOnAnotherDay_giveAnotherKey() throws NoSuchMethodException {
        Object yesterday = generatorAt("2026-12-31T20:59:59Z").generate(this, anyMethod(), LPU);
        Object today = generatorAt("2026-12-31T21:00:00Z").generate(this, anyMethod(), LPU);

        assertThat(today).isNotEqualTo(yesterday).isEqualTo(new SimpleKey(LocalDate.of(2027, 1, 1), LPU));
    }

    private static TodayKeyGenerator generatorAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new TodayKeyGenerator(new ReferenceCalendar(clock, TestApiProperties.defaults()));
    }

    private static Method anyMethod() throws NoSuchMethodException {
        return Object.class.getMethod("toString");
    }
}
