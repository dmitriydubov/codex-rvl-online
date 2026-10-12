package ru.projectx.datareference.service;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PostgresDurations {

    private static final Pattern VALUE = Pattern.compile("(\\d+)\\s*(us|ms|s|min|h|d)?");
    private static final Map<String, ChronoUnit> UNITS = Map.of(
            "us", ChronoUnit.MICROS,
            "ms", ChronoUnit.MILLIS,
            "s", ChronoUnit.SECONDS,
            "min", ChronoUnit.MINUTES,
            "h", ChronoUnit.HOURS,
            "d", ChronoUnit.DAYS);

    private PostgresDurations() {
    }

    static Optional<Duration> parse(String value) {
        Matcher matcher = VALUE.matcher(value.strip());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        ChronoUnit unit = Optional.ofNullable(matcher.group(2)).map(UNITS::get).orElse(ChronoUnit.MILLIS);
        return Optional.of(Duration.of(Long.parseLong(matcher.group(1)), unit));
    }
}
