package ru.projectx.contracts.acceptance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class OrderingStub implements AutoCloseable {

    static final String ORDERING_PATH = "/ordering";
    static final String TOKEN_PATH = "/realms/stand/protocol/openid-connect/token";

    private static final String ORDER_ID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern CREATED = Pattern.compile("создан (" + ORDER_ID + ")(?: (без ответа|с обрывом))?");
    private static final Pattern HTTP = Pattern.compile("HTTP (\\d{3})");
    private static final Pattern ERROR = Pattern.compile("ошибка (\\S+) HTTP (\\d{3})");
    private static final Pattern TOKEN = Pattern.compile("токен (\\S+) на (\\d+) секунд");
    private static final Pattern CLIENT_ORDERS = Pattern.compile("/orders/([^/]+)");
    private static final Pattern ORDER_CANCEL = Pattern.compile("/orders/([^/]+)/cancel");
    private static final Pattern ORDER_CHECKOUT = Pattern.compile("/orders/([^/]+)/checkout");
    private static final Pattern INVOICE =
            Pattern.compile("счёт (" + ORDER_ID + ") со ссылкой (\\S+)(?: (без ответа|с обрывом))?");
    private static final String STATE = "состояние";
    private static final String CHECKOUT = "счёт";
    private static final Map<Pattern, String> ORDER_ACTIONS = Map.of(
            Pattern.compile("/orders"), "создание",
            ORDER_CHECKOUT, CHECKOUT,
            ORDER_CANCEL, "отмена");
    private static final String FAILURE = "ошибка \\S+ HTTP \\d{3}|HTTP [45]\\d{2}|без ответа|обрыв";
    private static final Map<String, Pattern> KNOWN_ANSWERS = Map.of(
            "token", Pattern.compile(TOKEN.pattern() + "|" + FAILURE),
            "создание", Pattern.compile(CREATED.pattern() + "|" + FAILURE),
            "поиск", Pattern.compile("найденные (?:объектом|списком)|" + FAILURE),
            "отмена", Pattern.compile("отменён(?: без ответа)?|" + FAILURE),
            CHECKOUT, Pattern.compile(INVOICE.pattern() + "|" + FAILURE),
            STATE, Pattern.compile(STATE + "|" + FAILURE));
    private static final long SILENCE_SECONDS = 5;

    record Call(String kind, String method, String path, Map<String, String> headers, JsonNode body) {
    }

    private final JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();
    private final Map<String, StubOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, StubScript> scripts = new ConcurrentHashMap<>();
    private final StubOrderViews views = new StubOrderViews(json);
    private final Clock clock;
    private volatile String errorText = "текст ошибки стенда";
    private volatile Runnable beforeCreatedAnswer = () -> { };
    private volatile Runnable beforeInvoiceAnswer = () -> { };
    private volatile CountDownLatch silence = new CountDownLatch(1);

    OrderingStub(Clock clock) throws IOException {
        this.clock = clock;
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(ORDERING_PATH, this::ordering);
        server.createContext(TOKEN_PATH, this::token);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        reset();
    }

    String orderingUrl() {
        return "http://localhost:" + server.getAddress().getPort() + ORDERING_PATH;
    }

    String tokenUri() {
        return "http://localhost:" + server.getAddress().getPort() + TOKEN_PATH;
    }

    void reset() {
        silence.countDown();
        silence = new CountDownLatch(1);
        calls.clear();
        tokenRequests.clear();
        orders.clear();
        errorText = "текст ошибки стенда";
        beforeCreatedAnswer = () -> { };
        scripts.put("token", new StubScript("токен tok-stand-default на 600 секунд"));
        scripts.put("создание", new StubScript("HTTP 503"));
        scripts.put("поиск", new StubScript("найденные объектом"));
        scripts.put("отмена", new StubScript("отменён"));
        resetInvoicing();
    }

    void resetInvoicing() {
        beforeInvoiceAnswer = () -> { };
        scripts.put(CHECKOUT, new StubScript("HTTP 503"));
        scripts.put(STATE, new StubScript(STATE));
    }

    void answer(String kind, String answers) {
        StubScript script = new StubScript(answers);
        script.firstUnknown(KNOWN_ANSWERS.get(kind)).ifPresent(unknown -> {
            throw new IllegalArgumentException("стенд не знает ответа «" + unknown + "» на " + kind);
        });
        scripts.put(kind, script);
    }

    void errorText(String text) {
        this.errorText = text;
    }

    void createThenAnswerAfter(String orderId, Runnable action) {
        beforeCreatedAnswer = action;
        answer("создание", "создан " + orderId);
    }

    void invoiceThenAnswerAfter(String invoiceId, String paymentUrl, Runnable action) {
        beforeInvoiceAnswer = action;
        answer(CHECKOUT, "счёт " + invoiceId + " со ссылкой " + paymentUrl);
    }

    StubOrderViews views() {
        return views;
    }

    List<Call> calls() {
        return List.copyOf(calls);
    }

    List<Map<String, String>> tokenRequests() {
        return List.copyOf(tokenRequests);
    }

    long ordersOf(String clientId) {
        return orders.values().stream().filter(order -> order.clientId().equals(clientId)).count();
    }

    StubOrder onlyOrder() {
        if (orders.size() != 1) {
            throw new IllegalStateException("шагу нужен единственный заказ заглушки Ordering, их " + orders.size());
        }
        return orders.values().iterator().next();
    }

    Optional<StubOrder> order(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public void close() {
        silence.countDown();
        server.stop(0);
    }

    private void token(HttpExchange exchange) throws IOException {
        tokenRequests.add(StubHttp.form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        String answer = scripts.get("token").next();
        Matcher token = TOKEN.matcher(answer);
        if (token.matches()) {
            StubHttp.send(exchange, 200, "{\"access_token\": \"" + token.group(1) + "\", \"token_type\": \"Bearer\", "
                    + "\"expires_in\": " + token.group(2) + "}");
            return;
        }
        failOrSilence(exchange, answer);
    }

    private void ordering(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().substring(ORDERING_PATH.length());
        byte[] raw = exchange.getRequestBody().readAllBytes();
        JsonNode body = raw.length == 0 ? json.missingNode() : json.readTree(raw);
        String kind = kind(exchange.getRequestMethod(), path);
        calls.add(new Call(kind, exchange.getRequestMethod(), path, StubHttp.headers(exchange), body));
        switch (kind) {
            case "создание" -> create(exchange, body);
            case "поиск" -> find(exchange, path, body);
            case STATE -> state(exchange, path);
            case "отмена" -> cancel(exchange, path);
            case CHECKOUT -> checkout(exchange, path);
            default -> StubHttp.send(exchange, 404, "{}");
        }
    }

    private static String kind(String method, String path) {
        if ("GET".equals(method)) {
            return STATE;
        }
        return ORDER_ACTIONS.entrySet().stream()
                .filter(action -> action.getKey().matcher(path).matches())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("поиск");
    }

    private void create(HttpExchange exchange, JsonNode body) throws IOException {
        String answer = scripts.get("создание").next();
        Matcher created = CREATED.matcher(answer);
        if (!created.matches()) {
            failOrSilence(exchange, answer);
            return;
        }
        String orderId = created.group(1);
        orders.put(orderId, new StubOrder(orderId, body, clock.instant()));
        if (created.group(2) != null) {
            failOrSilence(exchange, "с обрывом".equals(created.group(2)) ? "обрыв" : "без ответа");
            return;
        }
        beforeCreatedAnswer.run();
        beforeCreatedAnswer = () -> { };
        StubHttp.send(exchange, 200, "{\"status\": \"success\", \"orderId\": \"" + orderId + "\"}");
    }

    private void find(HttpExchange exchange, String path, JsonNode body) throws IOException {
        String answer = scripts.get("поиск").next();
        if (!answer.startsWith("найденные")) {
            failOrSilence(exchange, answer);
            return;
        }
        Matcher client = CLIENT_ORDERS.matcher(path);
        String clientId = client.matches() ? client.group(1) : "";
        String documentId = body.path("orderDetails").path("documentId").asString("");
        List<StubOrder> found = orders.values().stream()
                .filter(order -> order.clientId().equals(clientId) && order.documentId().equals(documentId))
                .toList();
        StubHttp.send(exchange, 200, json.writeValueAsString(views.searchResult(found, answer.endsWith("списком"))));
    }

    private void state(HttpExchange exchange, String path) throws IOException {
        String answer = scripts.get(STATE).next();
        if (!STATE.equals(answer)) {
            failOrSilence(exchange, answer);
            return;
        }
        Optional<StubOrder> order = order(path.substring("/orders/".length()));
        if (order.isEmpty()) {
            StubHttp.send(exchange, 200, error("OrderDataService.getOrderById.getOrderData"));
            return;
        }
        ObjectNode response = json.createObjectNode().put("status", "success");
        response.set("data", views.state(order.get()));
        StubHttp.send(exchange, 200, json.writeValueAsString(response));
    }

    private void cancel(HttpExchange exchange, String path) throws IOException {
        String orderId = ORDER_CANCEL.matcher(path).replaceAll("$1");
        String answer = scripts.get("отмена").next();
        if (!answer.startsWith("отменён")) {
            failOrSilence(exchange, answer);
            return;
        }
        order(orderId).ifPresent(order -> order.enter(StubOrder.CANCELED));
        if ("отменён без ответа".equals(answer)) {
            failOrSilence(exchange, "без ответа");
            return;
        }
        StubHttp.send(exchange, 200, "{\"status\": \"success\"}");
    }

    private void checkout(HttpExchange exchange, String path) throws IOException {
        String answer = scripts.get(CHECKOUT).next();
        Matcher invoice = INVOICE.matcher(answer);
        if (!invoice.matches()) {
            failOrSilence(exchange, answer);
            return;
        }
        order(ORDER_CHECKOUT.matcher(path).replaceAll("$1")).ifPresent(order -> order.invoice(invoice.group(1)));
        if (invoice.group(3) != null) {
            failOrSilence(exchange, "с обрывом".equals(invoice.group(3)) ? "обрыв" : "без ответа");
            return;
        }
        beforeInvoiceAnswer.run();
        beforeInvoiceAnswer = () -> { };
        ObjectNode response = json.createObjectNode()
                .put("status", "success")
                .put("invoiceId", invoice.group(1))
                .put("paymentUrl", invoice.group(2))
                .put("paymentShortUrl", invoice.group(2) + "/s");
        StubHttp.send(exchange, 200, json.writeValueAsString(response));
    }

    private void failOrSilence(HttpExchange exchange, String answer) throws IOException {
        Matcher error = ERROR.matcher(answer);
        Matcher http = HTTP.matcher(answer);
        if (error.matches()) {
            StubHttp.send(exchange, Integer.parseInt(error.group(2)), error(error.group(1)));
        }
        else if (http.matches()) {
            StubHttp.sendEmpty(exchange, Integer.parseInt(http.group(1)));
        }
        else if ("без ответа".equals(answer)) {
            awaitSilence();
            exchange.close();
        }
        else if ("обрыв".equals(answer)) {
            exchange.close();
        }
        else {
            throw new IllegalArgumentException("стенд не знает ответа «" + answer + "»");
        }
    }

    private String error(String code) {
        ObjectNode response = json.createObjectNode().put("status", "error");
        response.putArray("errors").addObject()
                .put("index", 0)
                .put("code", code)
                .put("message", errorText)
                .put("isCritical", true);
        return json.writeValueAsString(response);
    }

    private void awaitSilence() {
        try {
            silence.await(SILENCE_SECONDS, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
