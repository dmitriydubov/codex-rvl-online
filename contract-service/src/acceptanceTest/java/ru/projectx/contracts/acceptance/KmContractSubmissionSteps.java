package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.GetResponse;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import ru.projectx.contracts.config.KmContractSubmissionProperties;
import ru.projectx.contracts.service.km.KmContractAwaiting;
import ru.projectx.contracts.service.km.KmContractSubmissionScheduler;
import tools.jackson.databind.JsonNode;

public class KmContractSubmissionSteps {

    private static final Duration PATIENCE = Duration.ofSeconds(10);
    private static final String ABSENT = "нет";
    private static final String EMPTY_ARRAY = "[]";
    private static final Pattern SEGMENT = Pattern.compile("([a-z_0-9]+)(?:\\[(\\d+)])?");

    private final KmContractSubmissionScheduler scheduler;
    private final KmContractAwaiting awaiting;
    private final KmContractSubmissionProperties settings;
    private final StandQueues queues;
    private final ReceivedKmContractSubmissions received;
    private final RememberedMessageIds rememberedMessageIds;
    private final OutboxRecords journal;
    private final IssuanceRecords records;
    private final CertificateRecords certificates;
    private final TaskMessages messages;

    public KmContractSubmissionSteps(KmContractSubmissionScheduler scheduler, KmContractAwaiting awaiting,
            KmContractSubmissionProperties settings, StandQueues queues, ReceivedKmContractSubmissions received,
            RememberedMessageIds rememberedMessageIds, OutboxRecords journal, IssuanceRecords records,
            CertificateRecords certificates, TaskMessages messages) {
        this.scheduler = scheduler;
        this.awaiting = awaiting;
        this.settings = settings;
        this.queues = queues;
        this.received = received;
        this.rememberedMessageIds = rememberedMessageIds;
        this.journal = journal;
        this.records = records;
        this.certificates = certificates;
        this.messages = messages;
    }

    @Когда("Договоры готовят передачу договоров КМ в 1С:УСК")
    public void preparationPass() {
        scheduler.prepareDue();
    }

    @Когда("Договоры обновляют сводку договоров КМ, ждущих ответа 1С:УСК")
    public void summaryRefreshed() {
        awaiting.refresh();
    }

    @Дано("предел размера сообщения о договоре КМ на стенде — {long} байт")
    public void messageLimit(long limit) {
        assertThat(settings.maxMessageSize().toBytes()).as("предел размера сообщения о договоре КМ").isEqualTo(limit);
    }

    @Тогда("1С:УСК получает из своей очереди {string} передачи договоров КМ, всего за сценарий — {int}")
    public void receivedTotal(String queueName, int total) {
        OneCQueue source = (OneCQueue) queues.called(queueName);
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> {
            received.add(source.drain());
            assertThat(received.count()).isGreaterThanOrEqualTo(total);
        });
        received.add(source.drain());
        assertThat(received.count()).as("передач договоров КМ за сценарий").isEqualTo(total);
    }

    @Тогда("передача договора КМ №{int} доставлена с delivery_mode {int}")
    public void deliveryMode(int ordinal, int deliveryMode) {
        assertThat(received.number(ordinal).getProps().getDeliveryMode()).isEqualTo(deliveryMode);
    }

    @Тогда("message_id передачи договора КМ №{int} совпадает с message_id исходящего сообщения №{int} по заявке {string}, присвоенным до публикации")
    public void messageIdMatchesTheRememberedOne(int receivedOrdinal, int ordinal, String requestId) {
        UUID assigned = journal.number(ordinal, requestId).messageId();
        rememberedMessageIds.find(ordinal, requestId).ifPresent(remembered ->
                assertThat(assigned).as("message_id записи №%s не изменился после публикации", ordinal).isEqualTo(remembered));
        GetResponse message = received.number(receivedOrdinal);
        assertThat(message.getProps().getMessageId()).isEqualTo(assigned.toString());
    }

    @Тогда("передача договора КМ №{int} получена с конвертом:")
    public void envelope(int ordinal, Map<String, String> expected) {
        Envelopes.check(body(ordinal).path("meta"), expected);
    }

    @Тогда("передача договора КМ №{int}: договор КМ участника {string} заявки {string}, поля data:")
    public void data(int ordinal, String participantId, String requestId, Map<String, String> expected) {
        JsonNode data = body(ordinal).path("data");
        assertThat(data.path("id").stringValue()).as("data.id")
                .isEqualTo(records.latestContractId(participantId, requestId).toString());
        SoftAssertions softly = new SoftAssertions();
        expected.forEach((path, value) -> field(softly, data, path, value));
        softly.assertAll();
    }

    @Тогда("документ №{int} передачи договора КМ №{int} — справка по вложению {string}, расширение имени файла — {string}")
    public void document(int documentOrdinal, int ordinal, String attachment, String extension) {
        JsonNode document = body(ordinal).path("data").path("documents").path(documentOrdinal - 1);
        assertThat(document.isObject()).as("документ №%s передачи №%s", documentOrdinal, ordinal).isTrue();
        UUID documentId = certificates.byAttachment(attachment).id();
        assertThat(document.path("document_id").stringValue()).as("document_id").isEqualTo(documentId.toString());
        assertThat(document.path("file_name").stringValue()).as("file_name")
                .isEqualTo("certificate-" + documentId + "." + extension);
    }

    private static void field(SoftAssertions softly, JsonNode data, String path, String expected) {
        JsonNode node = node(data, path);
        if (ABSENT.equals(expected)) {
            softly.assertThat(node.isMissingNode()).as("поля %s нет", path).isTrue();
        }
        else if (EMPTY_ARRAY.equals(expected)) {
            softly.assertThat(node.isArray() && node.isEmpty()).as("поле %s — пустой массив", path).isTrue();
        }
        else if (node.isNumber()) {
            softly.assertThat(node.decimalValue()).as(path).isEqualByComparingTo(new BigDecimal(expected));
        }
        else {
            softly.assertThat(node.asString()).as(path).isEqualTo(expected);
        }
    }

    private static JsonNode node(JsonNode data, String path) {
        JsonNode node = data;
        Matcher segments = SEGMENT.matcher(path);
        while (segments.find()) {
            node = node.path(segments.group(1));
            if (segments.group(2) != null) {
                node = node.path(Integer.parseInt(segments.group(2)));
            }
        }
        return node;
    }

    private JsonNode body(int ordinal) {
        return messages.read(received.number(ordinal).getBody());
    }
}
