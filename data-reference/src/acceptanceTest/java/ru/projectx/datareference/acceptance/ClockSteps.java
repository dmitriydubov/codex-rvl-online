package ru.projectx.datareference.acceptance;

import io.cucumber.java.Before;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ClockSteps {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    private static final Duration TWO_SECONDS = Duration.ofSeconds(2);

    private final StandClock clock;

    public ClockSteps(StandClock clock) {
        this.clock = clock;
    }

    @Before
    public void clockRuns() {
        clock.resume();
    }

    @Дано("часы контура остановлены на {string} по Москве")
    public void clockStoppedAt(String moment) {
        clock.stopAt(LocalDateTime.parse(moment, MOMENT).atZone(MOSCOW).toInstant());
    }

    @Когда("на часах контура прошло {int} минут без одной секунды")
    public void minutesWithoutSecondPassed(int minutes) {
        clock.advance(Duration.ofMinutes(minutes).minus(ONE_SECOND));
    }

    @Когда("на часах контура прошли две секунды")
    public void twoSecondsPassed() {
        clock.advance(TWO_SECONDS);
    }
}
