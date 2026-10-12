package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.ru.Тогда;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

public class ServiceLogSteps {

    private static final Pattern SEGMENT = Pattern.compile("([a-z_]+)(?:\\[(\\d+)])?");

    private final ServiceJournal journal;
    private final InboxJournal inbox;
    private final PublishedMessages published;
    private final TaskMessages messages;
    private final TaskQueue queue;

    public ServiceLogSteps(ServiceJournal journal, InboxJournal inbox, PublishedMessages published,
            TaskMessages messages, TaskQueue queue) {
        this.journal = journal;
        this.inbox = inbox;
        this.published = published;
        this.messages = messages;
        this.queue = queue;
    }

    @Before(order = 20)
    public void startCollecting() {
        journal.start();
    }

    @After
    public void stopCollecting() {
        journal.stop();
    }

    @Тогда("в журнале Договоров нет записей уровня ERROR")
    public void noErrors() {
        assertThat(journal.events()).filteredOn(event -> event.getLevel() == Level.ERROR)
                .extracting(ILoggingEvent::getFormattedMessage).isEmpty();
    }

    @Тогда("в журнале Договоров есть запись уровня ERROR об окончательном отказе сообщения {string}: тип события {string}, попытка {int}, SQLSTATE {string}")
    public void rejectionLogged(String messageId, String eventType, int attempt, String sqlState) {
        List<ILoggingEvent> matching = journal.events().stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .filter(event -> event.getFormattedMessage().contains("сообщение " + messageId + " отклонено окончательно"))
                .toList();
        assertThat(matching).as("записи ERROR об окончательном отказе %s", messageId).hasSize(1);
        assertThat(matching.get(0).getFormattedMessage())
                .startsWith("сообщение " + messageId + " отклонено окончательно: тип события " + eventType
                        + ", попытка " + attempt + ", SQLSTATE " + sqlState + ": ");
        assertThat(matching.get(0).getThrowableProxy()).as("приложенное исключение").isNull();
    }

    @Тогда("в журнале Договоров есть запись уровня ERROR о потерянном сообщении очереди {string} с причиной {string}")
    public void lossLogged(String queueName, String reason) {
        String queue = this.queue.called(queueName).name();
        List<ILoggingEvent> matching = journal.events().stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .filter(event -> event.getFormattedMessage()
                        .startsWith("сообщение очереди " + queue + " потеряно: " + reason + " ("))
                .toList();
        assertThat(matching).as("записи ERROR о потерянном сообщении очереди %s", queue).hasSize(1);
        assertThat(matching.get(0).getThrowableProxy()).isNull();
    }

    @Тогда("значение поля {string} сообщения {string} есть в его теле, но не попало ни в причину исхода, ни в журнал Договоров")
    public void personalValueStaysInBody(String field, String messageId) {
        Optional<String> value = value(messages.read(published.of(messageId)), field);
        InboxJournal.Entry entry = inbox.find(messageId)
                .orElseThrow(() -> new AssertionError("в журнале входящих нет записи о сообщении " + messageId));
        if (value.isEmpty()) {
            assertThat(entry.payload()).as("тело без поля %s", field).satisfiesAnyOf(
                    body -> assertThat(body).isNull(),
                    body -> assertThat(messages.read(body.getBytes(StandardCharsets.UTF_8)).path("data").path("participants"))
                            .isEmpty());
            return;
        }
        String body = entry.payload() == null ? new String(published.of(messageId), StandardCharsets.UTF_8) : entry.payload();
        assertThat(body).as("тело сообщения").contains(value.get());
        assertThat(entry.errorMessage()).as("причина исхода").satisfiesAnyOf(
                reason -> assertThat(reason).isNull(),
                reason -> assertThat(reason).doesNotContain(value.get()));
        assertThat(journal.mentioning(value.get())).extracting(ILoggingEvent::getFormattedMessage)
                .as("записи журнала со значением поля %s", field).isEmpty();
    }

    @Тогда("значение поля {string} сообщения с дефектом конверта есть в опубликованных байтах, но не попало в журнал Договоров")
    public void personalValueOfDefectiveMessageStaysInBytes(String field) {
        String value = value(messages.read(published.defectiveEnvelopeSource()), field)
                .orElseThrow(() -> new AssertionError("в опубликованном сообщении нет поля " + field));
        assertThat(new String(published.defectiveEnvelope(), StandardCharsets.UTF_8)).contains(value);
        assertThat(journal.mentioning(value)).extracting(ILoggingEvent::getFormattedMessage)
                .as("записи журнала со значением поля %s", field).isEmpty();
    }

    private static Optional<String> value(JsonNode body, String field) {
        JsonNode node = body.path("data");
        Matcher segments = SEGMENT.matcher(field);
        while (segments.find()) {
            node = node.path(segments.group(1));
            if (segments.group(2) != null) {
                node = node.path(Integer.parseInt(segments.group(2)));
            }
        }
        return node.isString() ? Optional.of(node.stringValue()) : Optional.empty();
    }
}
