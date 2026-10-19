package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.core.QueueInformation;
import ru.projectx.datareference.mapper.value.OneCGuid;

public class OneCBrokerSteps {

    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(30);

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Map<String, byte[]> BEFORE_ENVELOPE = Map.of("UTF-8 BOM", UTF8_BOM, "ничего", new byte[0]);

    private static final Map<String, String> BODY_DEFECTS = Map.of(
            "JSON-escape нулевого символа U+0000", "\\u0000",
            "JSON-escape одиночного суррогата U+D83D", "\\ud83d");

    private static final String DEFECTIVE_ENVELOPE = """
            {"data":{"guid":"1a2b3c4d-0000-4000-8000-000000000001","Наименование":"дефект %s"},\
            "metadata":{"message-id":"%s","topic":"","producer":"1c-usk"}}""";

    private final OneCBroker broker;
    private final OneCInbox inbox;
    private final QuarantineRows quarantine;

    public OneCBrokerSteps(OneCBroker broker, OneCInbox inbox, QuarantineRows quarantine) {
        this.broker = broker;
        this.inbox = inbox;
        this.quarantine = quarantine;
    }

    @Когда("1С:УСК публикует в очередь {string} сообщение {string} с дефектом тела {string}")
    public void defectivePublished(String stream, String messageId, String defect) {
        assertThat(BODY_DEFECTS).as("дефекты тела, которые умеет изображать шаг").containsKey(defect);
        UUID id = canonical(messageId);
        String envelope = DEFECTIVE_ENVELOPE.formatted(BODY_DEFECTS.get(defect), id);
        broker.publish(broker.queueOf(stream), id, envelope.getBytes(StandardCharsets.UTF_8));
    }

    @Когда("1С:УСК публикует в свою очередь договор страхования ДМС:")
    public void insuranceContractPublished(Map<String, String> contract) {
        publishContract(contract);
    }

    @Когда("1С:УСК публикует в свою очередь договоры страхования ДМС по порядку:")
    public void insuranceContractsPublishedInOrder(List<Map<String, String>> contracts) {
        contracts.forEach(this::publishContract);
    }

    @Тогда("очередь {string} пуста и у неё есть потребитель")
    public void queueEmptyWithConsumer(String stream) {
        String queue = broker.queueOf(stream);
        await().atMost(SETTLE_TIMEOUT).untilAsserted(() -> {
            QueueInformation info = broker.queue(queue);
            assertThat(info.getConsumerCount()).as("потребителей у очереди %s", queue).isPositive();
            assertThat(info.getMessageCount()).as("сообщений ждёт в очереди %s", queue).isZero();
            assertThat(broker.publishedTo(queue)).as("опубликованные в %s сообщения приняты", queue)
                    .allSatisfy(id -> assertThat(inbox.stored(id) || quarantine.contains(id)).isTrue());
        });
    }

    @Тогда("в очереди {string} лежат все опубликованные сообщения и у неё нет потребителя")
    public void queueHoldsEverythingWithoutConsumer(String stream) {
        String queue = broker.queueOf(stream);
        await().atMost(SETTLE_TIMEOUT).untilAsserted(() -> {
            QueueInformation info = broker.queue(queue);
            assertThat(info.getConsumerCount()).as("потребителей у очереди %s", queue).isZero();
            assertThat(info.getMessageCount()).as("сообщений ждёт в очереди %s", queue)
                    .isEqualTo(broker.publishedTo(queue).size());
        });
    }

    @Тогда("у очереди {string} нет потребителя")
    public void queueWithoutConsumer(String stream) {
        String queue = broker.queueOf(stream);
        await().atMost(SETTLE_TIMEOUT).untilAsserted(
                () -> assertThat(broker.queue(queue).getConsumerCount()).as("потребителей у %s", queue).isZero());
    }

    @Тогда("у очереди {string} есть потребитель")
    public void queueWithConsumer(String stream) {
        String queue = broker.queueOf(stream);
        await().atMost(SETTLE_TIMEOUT).untilAsserted(
                () -> assertThat(broker.queue(queue).getConsumerCount()).as("потребителей у %s", queue).isPositive());
    }

    @Дано("сырьё в порядке приёма — сообщения {string}")
    public void rawInOrder(String messageIds) {
        List<UUID> expected = List.of(messageIds.split("\\s*,\\s*")).stream().map(OneCBrokerSteps::canonical).toList();
        assertThat(inbox.messageIdsInOrder()).as("message-id сырья в порядке приёма").containsExactlyElementsOf(expected);
    }

    @Тогда("сырья сообщения {string} нет")
    public void noRaw(String messageId) {
        assertThat(inbox.stored(canonical(messageId))).as("строка сырья сообщения %s", messageId).isFalse();
    }

    private void publishContract(Map<String, String> contract) {
        OneCMessage message = OneCMessages.insuranceContract(contract);
        byte[] prefix = BEFORE_ENVELOPE.get(contract.getOrDefault(OneCMessages.BEFORE_ENVELOPE, "ничего"));
        assertThat(prefix).as("байты перед конвертом, которые умеет изображать шаг").isNotNull();
        byte[] envelope = message.envelope().getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + envelope.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(envelope, 0, body, prefix.length, envelope.length);
        inbox.expect(message);
        broker.publish(message.queue(), message.messageId(), body);
    }

    private static UUID canonical(String messageId) {
        return OneCGuid.parse(messageId).orElseThrow(
                () -> new IllegalArgumentException("message-id в сценарии — не канонический GUID: " + messageId));
    }
}
