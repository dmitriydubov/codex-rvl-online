package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.cucumber.java.ru.Тогда;

public class LmPolicyLogSteps {

    private final ServiceJournal journal;
    private final IssuanceRecords records;

    public LmPolicyLogSteps(ServiceJournal journal, IssuanceRecords records) {
        this.journal = journal;
        this.records = records;
    }

    @Тогда("в журнале Договоров нет записей уровня WARN по полису ЛМ участника {string} заявки {string}")
    public void noPolicyWarnings(String participant, String request) {
        String prefix = "полис ЛМ " + records.latestLmPolicyId(participant, request) + " участника " + participant
                + " заявки " + request + ":";
        assertThat(journal.events()).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .as("записи WARN по полису ЛМ участника %s", participant)
                .noneMatch(message -> message.startsWith(prefix));
    }
}
