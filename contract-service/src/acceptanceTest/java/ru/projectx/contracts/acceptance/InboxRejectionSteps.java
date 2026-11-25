package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import io.cucumber.java.ru.Тогда;

public class InboxRejectionSteps {

    private final InboxJournal inbox;
    private final ServiceJournal logs;

    public InboxRejectionSteps(InboxJournal inbox, ServiceJournal logs) {
        this.inbox = inbox;
        this.logs = logs;
    }

    @Тогда("повторный разбор сообщения {string} не назначен")
    public void noRetryScheduled(String messageId) {
        assertThat(entry(messageId).nextAttemptAt()).as("следующая попытка разбора").isNull();
    }

    @Тогда("причина отказа сообщения {string} записана в журнале Договоров без приложенного исключения и без значения {string} в журнале")
    public void rejectionLogOmitsValue(String messageId, String value) {
        var entry = entry(messageId);
        String prefix = "сообщение " + messageId + " отклонено окончательно:";
        var matching = logs.events().stream().filter(event -> event.getLevel() == Level.ERROR)
                .filter(event -> event.getFormattedMessage().startsWith(prefix)).toList();
        assertThat(matching).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage())
                    .isEqualTo("%s тип события %s, попытка %d, SQLSTATE нет: %s", prefix,
                            entry.eventType(), entry.attempts(), entry.errorMessage());
            assertThat(event.getThrowableProxy()).isNull();
        });
        assertThat(entry.errorMessage()).doesNotContain(value);
        assertThat(logs.mentioning(value)).isEmpty();
    }

    private InboxJournal.Entry entry(String messageId) {
        return inbox.find(messageId).orElseThrow(() -> new AssertionError("сообщение " + messageId + " не принято"));
    }
}
