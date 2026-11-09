package ru.projectx.contracts.service.integration.ordering;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class OrderingTestServer implements AutoCloseable {

    private final HttpServer server;
    private final Deque<Answer> tokenAnswers = new ArrayDeque<>();
    private final Deque<Answer> orderingAnswers = new ArrayDeque<>();
    private final List<Received> tokenRequests = new CopyOnWriteArrayList<>();
    private final List<Received> orderingRequests = new CopyOnWriteArrayList<>();
    private final CountDownLatch released = new CountDownLatch(1);

    OrderingTestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/token", exchange -> answer(exchange, tokenAnswers, tokenRequests));
        server.createContext("/ordering", exchange -> answer(exchange, orderingAnswers, orderingRequests));
        server.start();
    }

    String orderingUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/ordering";
    }

    String tokenUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
    }

    synchronized void token(Answer answer) {
        tokenAnswers.add(answer);
    }

    synchronized void ordering(Answer answer) {
        orderingAnswers.add(answer);
    }

    List<Received> tokenRequests() {
        return tokenRequests;
    }

    List<Received> orderingRequests() {
        return orderingRequests;
    }

    static Answer json(int status, String body) {
        return exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        };
    }

    static Answer accessToken(String token, int seconds) {
        return json(200, "{\"access_token\": \"" + token + "\", \"token_type\": \"Bearer\", \"expires_in\": "
                + seconds + "}");
    }

    Answer silence() {
        return exchange -> {
            try {
                released.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        };
    }

    static Answer dropped() {
        return HttpExchange::close;
    }

    @Override
    public void close() {
        released.countDown();
        server.stop(0);
    }

    private void answer(HttpExchange exchange, Deque<Answer> answers, List<Received> received) throws IOException {
        received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                Map.copyOf(exchange.getRequestHeaders()),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        next(answers).answer(exchange);
    }

    private synchronized Answer next(Deque<Answer> answers) {
        return answers.size() > 1 ? answers.poll() : answers.getFirst();
    }

    interface Answer {
        void answer(HttpExchange exchange) throws IOException;
    }

    record Received(String method, String path, Map<String, List<String>> headers, String body) {

        String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst())
                    .findFirst()
                    .orElse("");
        }
    }
}
