package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import ru.projectx.contracts.config.broker.MarketingBrokerProperties;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import tools.jackson.databind.node.ObjectNode;

public class OrderEventSteps {

    private static final Set<String> SETTINGS = Set.of("очередь событий заказов", "срок оплаты",
            "минимальный срок оплаты", "запас после срока оплаты", "порог сверки", "порог ожидания счёта",
            "повторов подтверждения");

    private final OrderEventQueue queue;
    private final OrderEventMessages messages;
    private final PublishedMessages published;
    private final OrderingStub stub;
    private final MarketingBrokerProperties marketing;
    private final PaymentOrderPassesProperties passes;
    private final ConsumedQueues consumers;
    private boolean queueDeleted;

    public OrderEventSteps(OrderEventQueue queue, OrderEventMessages messages, PublishedMessages published,
            OrderingStub stub, MarketingBrokerProperties marketing, PaymentOrderPassesProperties passes,
            ConsumedQueues consumers) {
        this.queue = queue;
        this.messages = messages;
        this.published = published;
        this.stub = stub;
        this.marketing = marketing;
        this.passes = passes;
        this.consumers = consumers;
    }

    @Дано("счёт и события оплаты на стенде настроены:")
    public void invoicingConfigured(Map<String, String> settings) {
        StepTables.onlyKnown(settings, SETTINGS);
        assertThat(Map.of(
                "очередь событий заказов", marketing.orderEvents().queue(),
                "срок оплаты", passes.invoices().paymentTerm().toString(),
                "минимальный срок оплаты", passes.invoices().minPaymentTerm().toString(),
                "запас после срока оплаты", passes.reconciliation().paymentGrace().toString(),
                "порог сверки", passes.reconciliation().threshold().toString(),
                "порог ожидания счёта", passes.invoices().awaitingThreshold().toString(),
                "повторов подтверждения", String.valueOf(passes.reconciliation().fixRepeats())))
                .as("настройки счёта и событий оплаты на стенде").containsAllEntriesOf(settings);
        assertThat(queue.name()).as("очередь событий заказов на стенде").isEqualTo(marketing.orderEvents().queue());
        queue.declare();
        queue.purge();
        stub.resetInvoicing();
    }

    @Когда("Ordering публикует в очередь событий продукта событие {string} типа {string} по заказу {string}")
    public void eventPublished(String eventId, String type, String orderId) {
        publish(eventId, type, orderId, event -> { });
    }

    @Когда("Ordering публикует в очередь событий продукта событие {string} типа {string} по заказу {string}:")
    public void eventPublishedWith(String eventId, String type, String orderId, Map<String, String> fields) {
        publish(eventId, type, orderId, event -> fields.forEach((path, value) ->
                OrderEventMessages.set(event, path, value)));
    }

    @Когда("Ordering публикует в очередь событий продукта событие {string} типа {string} по заказу {string} с отличием {string}")
    public void eventPublishedWithDifference(String eventId, String type, String orderId, String difference) {
        publish(eventId, type, orderId, event -> OrderEventMessages.differ(event, difference));
    }

    @Когда("Ordering публикует в очередь событий продукта сообщение с дефектом {string}")
    public void defectiveMessagePublished(String defect) {
        queue.publish(messages.defective(defect));
    }

    @Дано("у брокера маркетинга удалена очередь событий заказов")
    public void queueDeleted() {
        queue.delete();
        queueDeleted = true;
    }

    @After
    public void restoreDeletedQueue() {
        if (queueDeleted) {
            queue.declare();
            consumers.restoreConsumerOf(queue.name());
            queueDeleted = false;
        }
    }

    void publish(String eventId, String type, String orderId, Consumer<ObjectNode> change) {
        ObjectNode event = messages.event(eventId, type, orderId);
        change.accept(event);
        messages.rememberInHistory(orderId, type, event);
        byte[] body = messages.bytes(event);
        String messageId = MessageSender.ORDERING.messageId(eventId);
        published.remember(messageId, body);
        published.rememberQueued(queue.alias(), messageId);
        queue.publish(body);
    }
}
