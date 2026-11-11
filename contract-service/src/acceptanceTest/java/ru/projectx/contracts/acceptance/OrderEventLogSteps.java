package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.cucumber.java.ru.Тогда;
import java.util.List;

public class OrderEventLogSteps {

    private static final String NOT_APPLIED = "событие Ordering не применено: ";
    private static final String MESSAGE_ID = "messageId";
    private static final String INDIVIDUAL_REQUEST_ID = "individualRequestId";

    private final ServiceJournal journal;

    public OrderEventLogSteps(ServiceJournal journal) {
        this.journal = journal;
    }

    @Тогда("в журнале Договоров есть запись уровня WARN о событии Ordering {string} с причиной {string}")
    public void eventNotApplied(String eventId, String reason) {
        List<ILoggingEvent> matching = about(eventId).stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .filter(event -> event.getFormattedMessage().equals(NOT_APPLIED + reason))
                .toList();
        assertThat(matching).as("записи WARN «%s%s» о событии %s", NOT_APPLIED, reason, eventId).hasSize(1);
        assertThat(matching.getFirst().getThrowableProxy()).as("приложенное исключение").isNull();
    }

    @Тогда("в записях журнала Договоров о событии Ordering {string} ключ MDC individualRequestId отсутствует или равен {string}")
    public void requestIsNotImpersonated(String eventId, String requestId) {
        List<ILoggingEvent> records = about(eventId);
        assertThat(records).as("записи журнала о событии %s", eventId).isNotEmpty();
        assertThat(records).as("записи журнала о событии %s", eventId).allSatisfy(event ->
                assertThat(event.getMDCPropertyMap().getOrDefault(INDIVIDUAL_REQUEST_ID, requestId))
                        .as("ключ MDC %s записи «%s»", INDIVIDUAL_REQUEST_ID, event.getFormattedMessage())
                        .isEqualTo(requestId));
    }

    private List<ILoggingEvent> about(String eventId) {
        String messageId = MessageSender.ORDERING.messageId(eventId);
        return journal.events().stream()
                .filter(event -> messageId.equals(event.getMDCPropertyMap().get(MESSAGE_ID)))
                .toList();
    }
}
