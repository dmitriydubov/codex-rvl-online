package ru.projectx.contracts.acceptance;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class StubHttp {

    private StubHttp() {
    }

    static Map<String, String> headers(HttpExchange exchange) {
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(java.util.Locale.ROOT),
                values.getFirst()));
        return headers;
    }

    static Map<String, String> form(String body) {
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

    static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}
