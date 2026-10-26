package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.cucumber.java.ru.Тогда;
import java.util.List;

public class KmContractLogSteps {

    private final ServiceJournal journal;
    private final IssuanceRecords records;

    public KmContractLogSteps(ServiceJournal journal, IssuanceRecords records) {
        this.journal = journal;
        this.records = records;
    }

    @Тогда("в журнале Договоров есть запись уровня WARN по договору КМ участника {string} заявки {string} с причиной {string}")
    public void contractWarningLogged(String participant, String request, String reason) {
        String expected = prefix(participant, request) + " " + reason;
        List<ILoggingEvent> matching = warnings().stream()
                .filter(event -> event.getFormattedMessage().equals(expected))
                .toList();
        assertThat(matching).as("записи WARN по договору КМ участника %s", participant).isNotEmpty();
        assertThat(matching).extracting(ILoggingEvent::getThrowableProxy).as("приложенное исключение").containsOnlyNulls();
    }

    @Тогда("в журнале Договоров есть запись уровня ERROR по договору КМ участника {string} заявки {string} с причиной {string}, SQLSTATE {string}")
    public void contractPreparationFailureLogged(String participant, String request, String reason, String sqlState) {
        journal.assertPreparationFailure(prefix(participant, request), reason, sqlState);
    }

    @Тогда("в журнале Договоров нет записей уровня WARN по договору КМ участника {string} заявки {string}")
    public void noContractWarnings(String participant, String request) {
        String prefix = prefix(participant, request);
        assertThat(warnings()).extracting(ILoggingEvent::getFormattedMessage)
                .as("записи WARN по договору КМ участника %s", participant)
                .noneMatch(message -> message.startsWith(prefix));
    }

    private String prefix(String participant, String request) {
        return "договор КМ " + records.latestContractId(participant, request) + " участника " + participant
                + " заявки " + request + ":";
    }

    private List<ILoggingEvent> warnings() {
        return journal.events().stream().filter(event -> event.getLevel() == Level.WARN).toList();
    }
}
