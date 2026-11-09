package ru.projectx.contracts.acceptance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class OrderingStub implements AutoCloseable {

    static final String ORDERING_PATH = "/ordering";
    static final String TOKEN_PATH = "/realms/stand/protocol/openid-connect/token";

    private static final Pattern CREATED = Pattern.compile("создан (\\S+)(?: (без ответа|с обрывом))?");
    private static final Pattern HTTP = Pattern.compile("HTTP (\\d{3})");
    private static final Pattern ERROR = Pattern.compile("ошибка (\\S+) HTTP (\\d{3})");
    private static final Pattern TOKEN = Pattern.compile("токен (\\S+) на (\\d+) секунд");
    private static final Pattern CLIENT_ORDERS = Pattern.compile("/orders/([^/]+)");
    private static final Pattern ORDER_CANCEL = Pattern.compile("/orders/([^/]+)/cancel");
    private static final long SILENCE_SECONDS = 5;

    record Call(String kind, String method, String path, Map<String, String> headers, JsonNode body) {
    }

    record Order(String orderId, String clientId, String documentId, String status) {

        Order cancelled() {
            return new Order(orderId, clientId, documentId, "CANCELED");
        }
    }

    private final JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, StubScript> scripts = new ConcurrentHashMap<>();
    private volatile String errorText = "текст ошибки стенда";
    private volatile Runnable beforeCreatedAnswer = () -> { };
    private volatile CountDownLatch silence = new CountDownLatch(1);

    OrderingStub() throws IOException {
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
    }

    void answer(String kind, String answers) {
        scripts.put(kind, new StubScript(answers));
    }

    void errorText(String text) {
        this.errorText = text;
    }

    void createThenAnswerAfter(String orderId, Runnable action) {
        beforeCreatedAnswer = action;
        answer("создание", "создан " + orderId);
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

    Optional<Order> order(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public void close() {
        silence.countDown();
        server.stop(0);
    }

    private void token(HttpExchange exchange) throws IOException {
        tokenRequests.add(form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        String answer = scripts.get("token").next();
        Matcher token = TOKEN.matcher(answer);
        if (token.matches()) {
            send(exchange, 200, "{\"access_token\": \"" + token.group(1) + "\", \"token_type\": \"Bearer\", "
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
        calls.add(new Call(kind, exchange.getRequestMethod(), path, headers(exchange), body));
        switch (kind) {
            case "создание" -> create(exchange, body);
            case "поиск" -> find(exchange, path, body);
            case "состояние" -> state(exchange, path);
            case "отмена" -> cancel(exchange, path);
            default -> send(exchange, 404, "{}");
        }
    }

    private static String kind(String method, String path) {
        if ("GET".equals(method)) {
            return "состояние";
        }
        if ("/orders".equals(path)) {
            return "создание";
        }
        return ORDER_CANCEL.matcher(path).matches() ? "отмена" : "поиск";
    }

    private void create(HttpExchange exchange, JsonNode body) throws IOException {
        String answer = scripts.get("создание").next();
        Matcher created = CREATED.matcher(answer);
        if (!created.matches()) {
            failOrSilence(exchange, answer);
            return;
        }
        String orderId = created.group(1);
        orders.put(orderId, new Order(orderId, body.path("clientId").asString(""),
                body.path("orderDetails").path(0).path("documentId").asString(""), "NEW"));
        if (created.group(2) != null) {
            failOrSilence(exchange, "с обрывом".equals(created.group(2)) ? "обрыв" : "без ответа");
            return;
        }
        beforeCreatedAnswer.run();
        beforeCreatedAnswer = () -> { };
        send(exchange, 200, "{\"status\": \"success\", \"orderId\": \"" + orderId + "\"}");
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
        List<ObjectNode> found = new ArrayList<>();
        orders.values().stream()
                .filter(order -> order.clientId().equals(clientId) && order.documentId().equals(documentId))
                .forEach(order -> found.add(orderNode(order)));
        ObjectNode response = json.createObjectNode().put("status", "success");
        if (answer.endsWith("списком")) {
            ArrayNode list = response.putArray("data");
            found.forEach(list::add);
        }
        else if (!found.isEmpty()) {
            response.set("data", found.getFirst());
        }
        send(exchange, 200, json.writeValueAsString(response));
    }

    private void state(HttpExchange exchange, String path) throws IOException {
        Optional<Order> order = order(path.substring("/orders/".length()));
        if (order.isEmpty()) {
            send(exchange, 200, error("OrderDataService.getOrderById.getOrderData"));
            return;
        }
        ObjectNode response = json.createObjectNode().put("status", "success");
        response.set("data", orderNode(order.get()));
        send(exchange, 200, json.writeValueAsString(response));
    }

    private void cancel(HttpExchange exchange, String path) throws IOException {
        String orderId = ORDER_CANCEL.matcher(path).replaceAll("$1");
        String answer = scripts.get("отмена").next();
        if (!answer.startsWith("отменён")) {
            failOrSilence(exchange, answer);
            return;
        }
        orders.computeIfPresent(orderId, (id, order) -> order.cancelled());
        if ("отменён без ответа".equals(answer)) {
            failOrSilence(exchange, "без ответа");
            return;
        }
        send(exchange, 200, "{\"status\": \"success\"}");
    }

    private ObjectNode orderNode(Order order) {
        ObjectNode node = json.createObjectNode()
                .put("clientId", order.clientId())
                .put("orderId", order.orderId())
                .put("status", order.status());
        node.putArray("orderDetails").addObject()
                .put("product", "rvl")
                .put("documentType", "contract")
                .put("documentId", order.documentId())
                .put("status", order.status());
        return node;
    }

    private void failOrSilence(HttpExchange exchange, String answer) throws IOException {
        Matcher error = ERROR.matcher(answer);
        Matcher http = HTTP.matcher(answer);
        if (error.matches()) {
            send(exchange, Integer.parseInt(error.group(2)), error(error.group(1)));
        }
        else if (http.matches()) {
            sendEmpty(exchange, Integer.parseInt(http.group(1)));
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

    private static Map<String, String> headers(HttpExchange exchange) {
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(java.util.Locale.ROOT),
                values.getFirst()));
        return headers;
    }

    private static Map<String, String> form(String body) {
        Map<String, String> fields = new ConcurrentHashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                fields.put(java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        return fields;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    static final class StubScript {

        private final List<String> answers;
        private int next;

        StubScript(String answers) {
            this.answers = List.of(answers.split(";\\s*"));
        }

        synchronized String next() {
            String answer = answers.get(Math.min(next, answers.size() - 1));
            next++;
            return answer.strip();
        }
    }
}
