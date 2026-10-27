package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.cucumber.java.ru.Тогда;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;

public class QuarantineSteps {

    private static final Duration PATIENCE = Duration.ofSeconds(40);

    private final QuarantineRecords quarantine;
    private final PublishedMessages published;
    private final TaskMessages messages;
    private final TaskQueue queue;
    private final ServiceJournal journal;

    public QuarantineSteps(QuarantineRecords quarantine, PublishedMessages published, TaskMessages messages,
            TaskQueue queue, ServiceJournal journal) {
        this.quarantine = quarantine;
        this.published = published;
        this.messages = messages;
        this.queue = queue;
        this.journal = journal;
    }

    @Тогда("сообщение {string} лежит в карантине очереди {string} с причиной {string}")
    public void quarantinedWithReason(String messageId, String queueAlias, String reason) {
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(quarantine.find(messageId)).isPresent());
        QuarantineRecords.Entry entry = entry(messageId);
        assertThat(entry.sourceQueue()).as("очередь-источник").isEqualTo(queue.called(queueAlias).name());
        assertThat(entry.reason()).as("причина карантина").isEqualTo(reason);
    }

    @Тогда("тело карантинной записи {string} — опубликованное тело байт в байт")
    public void bodyIsThePublishedBytes(String messageId) {
        assertThat(entry(messageId).body()).as("тело карантинной записи").isEqualTo(published.defectiveEnvelope());
    }

    @Тогда("тело карантинной записи {string} стёрто")
    public void bodyErased(String messageId) {
        assertThat(entry(messageId).body()).as("тело карантинной записи %s", messageId).isNull();
    }

    @Тогда("тело карантинной записи {string} сохранено")
    public void bodyKept(String messageId) {
        assertThat(entry(messageId).body()).as("тело карантинной записи %s", messageId).isNotNull();
    }

    @Тогда("в карантине ровно одна запись")
    public void exactlyOneRecord() {
        assertThat(quarantine.count()).isEqualTo(1);
    }

    @Тогда("в журнале Договоров есть запись уровня ERROR о карантине сообщения {string} очереди {string} с причиной {string}")
    public void quarantineLogged(String messageId, String queueAlias, String reason) {
        String prefix = "сообщение " + messageId + " из очереди " + queue.called(queueAlias).name()
                + " помещено в карантин: " + reason + " (";
        List<ILoggingEvent> matching = journal.events().stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .filter(event -> event.getFormattedMessage().startsWith(prefix))
                .toList();
        assertThat(matching).as("записи ERROR о карантине сообщения %s", messageId).hasSize(1);
        assertThat(matching.get(0).getThrowableProxy()).as("приложенное исключение").isNull();
    }

    @Тогда("значение поля {string} карантинного сообщения {string} есть в его теле, но не попало ни в причину карантина, ни в журнал Договоров")
    public void personalValueStaysInQuarantinedBody(String field, String messageId) {
        String value = ServiceLogSteps.value(messages.read(published.defectiveEnvelopeSource()), field)
                .orElseThrow(() -> new AssertionError("в опубликованном сообщении нет поля " + field));
        QuarantineRecords.Entry entry = entry(messageId);
        assertThat(entry.body()).as("тело карантинной записи").isNotNull();
        assertThat(new String(entry.body(), StandardCharsets.UTF_8)).as("тело карантинной записи").contains(value);
        assertThat(entry.reason()).as("причина карантина").doesNotContain(value);
        assertThat(journal.mentioning(value)).extracting(ILoggingEvent::getFormattedMessage)
                .as("записи журнала со значением поля %s", field).isEmpty();
    }

    private QuarantineRecords.Entry entry(String messageId) {
        return quarantine.find(messageId)
                .orElseThrow(() -> new AssertionError("в карантине нет записи о сообщении " + messageId));
    }
}
