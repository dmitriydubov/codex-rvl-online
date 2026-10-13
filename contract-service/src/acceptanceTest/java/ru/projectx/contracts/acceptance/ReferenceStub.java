package ru.projectx.contracts.acceptance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.exception.ReferenceKind;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class ReferenceStub implements AutoCloseable {

    static final String BASE_PATH = "/internal/v1";

    private static final Map<String, ReferenceKind> KINDS = Map.of(
            "программа", ReferenceKind.PROGRAM,
            "договор страхования", ReferenceKind.INSURANCE_CONTRACT,
            "полис", ReferenceKind.POLICY);
    private static final String REQUEST_ID = "X-Request-Id";
    private static final String EXTERNAL_ID_QUERY = "external_id=";
    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    private static final int UNAVAILABLE = 503;

    record Entry(ReferenceKind kind, UUID id, UUID externalId, @Nullable String number, boolean archived) {
    }

    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<ReferenceKind, Map<UUID, Entry>> entries = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> notFoundBeforeFound = new ConcurrentHashMap<>();
    private final Map<ReferenceKind, Boolean> unavailable = new ConcurrentHashMap<>();
    private final HttpServer server;

    ReferenceStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(BASE_PATH, this::handle);
        server.start();
    }

    static ReferenceKind kind(String alias) {
        return Optional.ofNullable(KINDS.get(alias))
                .orElseThrow(() -> new IllegalArgumentException("стенд не знает справочника «" + alias + "»"));
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort() + BASE_PATH;
    }

    void define(Entry entry) {
        entries.computeIfAbsent(entry.kind(), kind -> new ConcurrentHashMap<>()).put(entry.id(), entry);
        notFoundBeforeFound.remove(entry.externalId());
    }

    void appearsAfterNotFound(Entry policy, int notFoundResponses) {
        define(policy);
        notFoundBeforeFound.put(policy.externalId(), new AtomicInteger(notFoundResponses));
    }

    void archive(ReferenceKind kind, UUID id, boolean archived) {
        Entry entry = Optional.ofNullable(entries.getOrDefault(kind, Map.of()).get(id))
                .orElseThrow(() -> new IllegalStateException("у справочного сервиса нет записи " + kind + " " + id));
        define(new Entry(kind, id, entry.externalId(), entry.number(), archived));
    }

    void remove(ReferenceKind kind, UUID id) {
        entries.getOrDefault(kind, Map.of()).remove(id);
    }

    void unavailable(ReferenceKind kind) {
        unavailable.put(kind, true);
    }

    void unavailableForAll() {
        Arrays.stream(ReferenceKind.values()).forEach(this::unavailable);
    }

    void available() {
        unavailable.clear();
    }

    void reset() {
        entries.clear();
        notFoundBeforeFound.clear();
        unavailable.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Request request = Request.of(exchange.getRequestURI().getPath(), exchange.getRequestURI().getQuery());
        String requestId = Optional.ofNullable(exchange.getRequestHeaders().getFirst(REQUEST_ID)).orElse("");
        Response response = respond(request, requestId);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set(REQUEST_ID, requestId);
        byte[] body = json.writeValueAsString(response.body()).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private Response respond(Request request, String requestId) {
        if (unavailable.containsKey(request.kind())) {
            return new Response(UNAVAILABLE, error("SERVICE_UNAVAILABLE",
                    "сервис временно недоступен, повторите запрос позже", requestId));
        }
        return found(request)
                .map(entry -> new Response(OK, record(entry)))
                .orElseGet(() -> new Response(NOT_FOUND, error(request.kind().notFoundCode(),
                        request.kind().name().toLowerCase(java.util.Locale.ROOT) + " " + request.key() + " не найден",
                        requestId)));
    }

    private Optional<Entry> found(Request request) {
        Map<UUID, Entry> ofKind = entries.getOrDefault(request.kind(), Map.of());
        if (!request.byExternalId()) {
            return Optional.ofNullable(ofKind.get(request.key()));
        }
        AtomicInteger pending = notFoundBeforeFound.get(request.key());
        if (pending != null && pending.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
            return Optional.empty();
        }
        return ofKind.values().stream().filter(entry -> entry.externalId().equals(request.key())).findFirst();
    }

    private ObjectNode record(Entry entry) {
        ObjectNode body = json.createObjectNode()
                .put("id", entry.id().toString())
                .put("external_id", entry.externalId().toString())
                .put("is_archive", entry.archived());
        switch (entry.kind()) {
            case PROGRAM -> body.put("name", "Стенд: программа").put("type_programm", "СКЛ").putArray("services");
            case INSURANCE_CONTRACT -> body.put("number", "СТЕНД-ДМС-0001").put("begin_date", "2026-01-01")
                    .putNull("end_date");
            case POLICY -> body.put("number", entry.number()).put("begin_date", "2026-01-01")
                    .put("end_date", "2099-12-31");
        }
        return body;
    }

    private ObjectNode error(String code, String message, String requestId) {
        ObjectNode body = json.createObjectNode();
        body.putObject("error").put("code", code).put("message", message).put("request_id", requestId)
                .putArray("details");
        return body;
    }

    private record Response(int status, ObjectNode body) {
    }

    private record Request(ReferenceKind kind, UUID key, boolean byExternalId) {

        static Request of(String path, @Nullable String query) {
            String relative = path.substring(BASE_PATH.length());
            if (relative.equals("/policies/contract-view") && query != null && query.startsWith(EXTERNAL_ID_QUERY)) {
                return new Request(ReferenceKind.POLICY, UUID.fromString(query.substring(EXTERNAL_ID_QUERY.length())),
                        true);
            }
            String[] segments = relative.split("/");
            return switch (segments[1]) {
                case "programs" -> new Request(ReferenceKind.PROGRAM, UUID.fromString(segments[2]), false);
                case "insurance-contracts" -> new Request(ReferenceKind.INSURANCE_CONTRACT,
                        UUID.fromString(segments[2]), false);
                case "policies" -> new Request(ReferenceKind.POLICY, UUID.fromString(segments[2]), false);
                default -> throw new IllegalArgumentException("заглушка справочника не знает пути " + path);
            };
        }
    }
}
