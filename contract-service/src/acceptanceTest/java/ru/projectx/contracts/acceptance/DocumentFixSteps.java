package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Тогда;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class DocumentFixSteps {

    private static final String AS_ORDER = "как у заказа";

    private final DocumentFixQueue queue;
    private final PaymentOrderRecords orders;
    private final OrderingStub stub;
    private final InboxSteps inbox;
    private final LeaderLockStand leaderLock;
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Message> received = new ArrayList<>();

    public DocumentFixSteps(DocumentFixQueue queue, PaymentOrderRecords orders, OrderingStub stub, InboxSteps inbox,
            LeaderLockStand leaderLock) {
        this.queue = queue;
        this.orders = orders;
        this.stub = stub;
        this.inbox = inbox;
        this.leaderLock = leaderLock;
    }

    @Дано("Ordering создаёт заказ {string} и отвечает только после того, как Договоры разобрали присланные сообщения")
    public void answersAfterTheServiceProcessedMessages(String orderId) {
        stub.createThenAnswerAfter(orderId, inbox::everythingProcessed);
    }

    @Дано("Ordering создаёт заказ {string} и отвечает только после того, как другой экземпляр Договоров перехватил роль ведущего")
    public void answersAfterAnotherInstanceTookOver(String orderId) {
        stub.createThenAnswerAfter(orderId, leaderLock::takeOver);
    }

    @Тогда("Ordering получает из очереди подтверждений документов подтверждения, всего за сценарий — {int}")
    public void fixesReceived(int count) {
        received.addAll(queue.drain());
        assertThat(received).as("подтверждения документов в очереди %s", DocumentFixQueue.NAME).hasSize(count);
    }

    @Тогда("подтверждение документа №{int} получено:")
    public void fixReceived(int number, Map<String, String> expected) {
        Message message = received.get(number - 1);
        JsonNode body = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        expected.forEach((field, value) -> assertThat(actual(message, body, field))
                .as("поле «%s» подтверждения документа №%d", field, number).isEqualTo(resolved(value)));
    }

    private String resolved(String value) {
        return AS_ORDER.equals(value) ? orders.onlyOrder().get("id") : value;
    }

    private static String actual(Message message, JsonNode body, String field) {
        return switch (field) {
            case "обменник" -> message.getMessageProperties().getReceivedExchange();
            case "ключ маршрутизации" -> message.getMessageProperties().getReceivedRoutingKey();
            case "поля тела" -> String.join(", ", new TreeSet<>(body.propertyNames()));
            default -> at(body, field).asString("нет");
        };
    }

    private static JsonNode at(JsonNode body, String path) {
        JsonNode node = body;
        for (String part : path.split("\\.")) {
            node = node.path(part);
        }
        return node;
    }
}
