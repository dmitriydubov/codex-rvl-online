package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.service.schedulers.StuckTransferAlert;

public class OneCStuckTransferSteps {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Map<String, UnaryOperator<Duration>> AGE_AROUND_THRESHOLD = Map.of(
            "больше", threshold -> threshold.plus(HOUR),
            "меньше", threshold -> threshold.minus(HOUR));
    private static final String WARNING = "передачи прайсов не собрались за ";

    private final OneCInbox inbox;
    private final ImportProperties properties;
    private final StuckTransferAlert alert;
    private final ServiceJournal journal;
    private int eventsBeforeCheck = -1;
    private int eventsAfterCheck = -1;

    public OneCStuckTransferSteps(
            OneCInbox inbox, ImportProperties properties, StuckTransferAlert alert, ServiceJournal journal) {

        this.inbox = inbox;
        this.properties = properties;
        this.alert = alert;
        this.journal = journal;
    }

    @Дано("с момента приёма последнего сообщения прошло на час {word} порога зависания передач прайсов")
    public void messagesAgedAroundThreshold(String moreOrLess) {
        assertThat(AGE_AROUND_THRESHOLD).as("давность, которую умеет изображать шаг").containsKey(moreOrLess);
        inbox.ageAll(AGE_AROUND_THRESHOLD.get(moreOrLess).apply(properties.stuckTransferThreshold()));
    }

    @Когда("Дата-референс проверяет зависшие передачи прайсов")
    public void checkStuckTransfers() {
        eventsBeforeCheck = journal.events().size();
        alert.reportStuckTransfers();
        eventsAfterCheck = journal.events().size();
    }

    @Тогда("последняя проверка зависших передач прайсов предупредила о передаче {string}")
    public void lastCheckWarnedAbout(String cid) {
        assertThat(warningsOfLastCheck()).as("предупреждения последней проверки зависших передач")
                .anyMatch(event -> event.getFormattedMessage().contains(cid));
    }

    @Тогда("последняя проверка зависших передач прайсов ни о чём не предупредила")
    public void lastCheckWarnedAboutNothing() {
        assertThat(warningsOfLastCheck()).as("предупреждения последней проверки зависших передач").isEmpty();
    }

    private List<ILoggingEvent> warningsOfLastCheck() {
        assertThat(eventsBeforeCheck).as("проверку зависших передач выполнил шаг «Дата-референс проверяет …»")
                .isNotNegative();
        return journal.events().subList(eventsBeforeCheck, eventsAfterCheck).stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .filter(event -> event.getFormattedMessage().startsWith(WARNING))
                .toList();
    }
}
