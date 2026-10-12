package ru.projectx.contracts.acceptance;

import io.cucumber.java.Before;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import ru.projectx.contracts.config.InboxProperties;

public class ClockSteps {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final StandClock clock;
    private final InboxProperties inbox;

    public ClockSteps(StandClock clock, InboxProperties inbox) {
        this.clock = clock;
        this.inbox = inbox;
    }

    @Before
    public void clockRuns() {
        clock.resume();
    }

    @Дано("часы Договоров остановлены на {string} по Москве")
    public void clockStoppedAt(String moment) {
        clock.stopAt(LocalDateTime.parse(moment, MOMENT).atZone(MOSCOW).toInstant());
    }

    @Когда("на часах Договоров прошло {int} минут без одной секунды")
    public void minutesWithoutSecondPassed(int minutes) {
        clock.advance(Duration.ofMinutes(minutes).minus(ONE_SECOND));
    }

    @Когда("на часах Договоров прошло {int} минут")
    public void minutesPassed(int minutes) {
        clock.advance(Duration.ofMinutes(minutes));
    }

    @Когда("на часах Договоров прошла ещё секунда")
    public void secondPassed() {
        clock.advance(ONE_SECOND);
    }

    @Когда("на часах Договоров прошло на час больше срока хранения тел входящих сообщений")
    public void retentionPlusHourPassed() {
        clock.advance(inbox.retention().payload().plus(ONE_HOUR));
    }

    @Когда("на часах Договоров прошло на час меньше срока хранения тел входящих сообщений")
    public void retentionMinusHourPassed() {
        clock.advance(inbox.retention().payload().minus(ONE_HOUR));
    }
}
