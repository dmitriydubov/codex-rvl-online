package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Тогда;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;

public class OrderingStubSteps {

    private static final String AS_ORDER = "как у заказа";
    private static final String NONE = "нет";

    private final OrderingStub stub;
    private final PaymentOrderRecords orders;

    public OrderingStubSteps(OrderingStub stub, PaymentOrderRecords orders) {
        this.stub = stub;
        this.orders = orders;
    }

    @Дано("сервер токенов отвечает: {string}")
    public void tokenServerAnswers(String answers) {
        stub.answer("token", answers);
    }

    @Дано("Ordering отвечает на создание заказа: {string}")
    public void creationAnswers(String answers) {
        stub.answer("создание", answers);
    }

    @Дано("Ordering отвечает на поиск заказов клиента: {string}")
    public void searchAnswers(String answers) {
        stub.answer("поиск", answers);
    }

    @Дано("Ordering отвечает на отмену заказа: {string}")
    public void cancellationAnswers(String answers) {
        stub.answer("отмена", answers);
    }

    @Дано("Ordering пишет в текст ошибки {string}")
    public void errorText(String text) {
        stub.errorText(text);
    }

    @Тогда("вызовов Ordering за сценарий нет")
    public void noOrderingCalls() {
        assertThat(stub.calls()).extracting(OrderingStub.Call::kind).as("вызовы Ordering").isEmpty();
    }

    @Тогда("вызовы Ordering за сценарий — {string}")
    public void orderingCalls(String kinds) {
        assertThat(stub.calls()).extracting(OrderingStub.Call::kind).as("вызовы Ordering")
                .containsExactlyElementsOf(Arrays.stream(kinds.split(",")).map(String::strip).toList());
    }

    @Тогда("вызов Ordering №{int}:")
    public void orderingCall(int number, Map<String, String> expected) {
        List<OrderingStub.Call> calls = stub.calls();
        assertThat(calls).as("вызовы Ordering").hasSizeGreaterThanOrEqualTo(number);
        OrderingStub.Call call = calls.get(number - 1);
        expected.forEach((field, value) -> assertThat(actual(call, field))
                .as("поле «%s» вызова Ordering №%d", field, number).isEqualTo(resolved(field, value)));
    }

    @Тогда("все вызовы Ordering за сценарий несут correlationId заказа на оплату договора КМ участника {string} заявки {string}")
    public void everyCallCarriesTheCorrelationId(String participant, String request) {
        String correlationId = orders.single(participant, request).get("correlation_id");
        assertThat(stub.calls()).as("вызовы Ordering").isNotEmpty()
                .extracting(call -> call.headers().get("correlationid")).containsOnly(correlationId);
    }

    @Тогда("запросов к серверу токенов за сценарий — {int}")
    public void tokenRequests(int count) {
        assertThat(stub.tokenRequests()).as("запросы к серверу токенов").hasSize(count);
    }

    @Тогда("запрос к серверу токенов №{int}:")
    public void tokenRequest(int number, Map<String, String> expected) {
        Map<String, String> form = stub.tokenRequests().get(number - 1);
        expected.forEach((field, value) -> assertThat(form.get(field))
                .as("поле «%s» запроса к серверу токенов №%d", field, number).isEqualTo(value));
    }

    @Тогда("заказов в Ordering у клиента {string} — {int}")
    public void ordersOfClient(String clientId, int count) {
        assertThat(stub.ordersOf(clientId)).as("заказы клиента %s в Ordering", clientId).isEqualTo(count);
    }

    @Тогда("заказ {string} в Ordering — в статусе {string}")
    public void orderStatus(String orderId, String status) {
        assertThat(stub.order(orderId)).as("заказ %s в Ordering", orderId)
                .map(OrderingStub.Order::status).contains(status);
    }

    private String resolved(String field, String value) {
        if (!AS_ORDER.equals(value)) {
            return value;
        }
        Map<String, String> order = orders.onlyOrder();
        return "correlationId".equals(field) ? order.get("correlation_id") : order.get("id");
    }

    private static String actual(OrderingStub.Call call, String field) {
        return switch (field) {
            case "метод" -> call.method();
            case "путь" -> call.path();
            case "Authorization", "correlationId" -> Optional.ofNullable(
                    call.headers().get(field.toLowerCase(java.util.Locale.ROOT))).orElse(NONE);
            default -> bodyValue(call.body(), field);
        };
    }

    private static String bodyValue(JsonNode body, String field) {
        if ("поля тела".equals(field)) {
            return names(body);
        }
        if (field.startsWith("поля ")) {
            return names(at(body, field.substring("поля ".length())));
        }
        JsonNode value = at(body, field);
        return value.isMissingNode() ? NONE : value.isNumber() ? value.decimalValue().toPlainString() : value.asString();
    }

    private static JsonNode at(JsonNode body, String path) {
        JsonNode node = body;
        for (String part : path.split("\\.")) {
            int index = part.indexOf('[');
            node = index < 0 ? node.path(part)
                    : node.path(part.substring(0, index)).path(Integer.parseInt(part.substring(index + 1,
                            part.length() - 1)));
        }
        return node;
    }

    private static String names(JsonNode node) {
        return String.join(", ", new TreeSet<>(node.propertyNames()));
    }
}
