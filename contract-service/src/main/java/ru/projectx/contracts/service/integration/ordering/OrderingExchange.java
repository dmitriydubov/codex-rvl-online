package ru.projectx.contracts.service.integration.ordering;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

final class OrderingExchange {

    static final String CORRELATION_ID = "correlationId";
    static final int MAX_BODY_BYTES = 1024 * 1024;

    private static final String BEARER = "Bearer ";

    private final RestClient http;
    private final JsonMapper json;

    OrderingExchange(RestClient http, JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    OrderingReply post(OrderingCall call, String uri, JsonNode body, Object... variables) {
        return send(call, http.post().uri(uri, variables).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    OrderingReply get(OrderingCall call, String uri, Object... variables) {
        return send(call, http.get().uri(uri, variables));
    }

    private OrderingReply send(OrderingCall call, RestClient.RequestHeadersSpec<?> request) {
        try {
            return request
                    .headers(headers -> authorize(headers, call.token(), call.correlationId()))
                    .exchange((sent, response) -> new OrderingReply(
                            response.getStatusCode().value(), read(response.getBody())), true);
        }
        catch (ResourceAccessException io) {
            throw new OrderingUnavailableException(call.operation(), transport(io), io);
        }
        catch (RestClientException unreadable) {
            throw new OrderingUnavailableException(call.operation(), "ответ не прочитан", unreadable);
        }
    }

    private static void authorize(HttpHeaders headers, String token, UUID correlationId) {
        headers.set(HttpHeaders.AUTHORIZATION, BEARER + token);
        headers.set(CORRELATION_ID, correlationId.toString());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    }

    private JsonNode read(InputStream body) throws IOException {
        byte[] head = body.readNBytes(MAX_BODY_BYTES + 1);
        return head.length > MAX_BODY_BYTES ? MissingNode.getInstance() : parse(head);
    }

    private JsonNode parse(byte[] body) {
        try {
            return body.length == 0 ? MissingNode.getInstance() : json.readTree(body);
        }
        catch (JacksonException unreadable) {
            return MissingNode.getInstance();
        }
    }

    private static String transport(ResourceAccessException io) {
        return io.getCause() instanceof HttpTimeoutException ? "нет ответа за время ожидания" : "соединение прервано";
    }

    record OrderingCall(OrderingOperation operation, String token, UUID correlationId) {
    }
}
