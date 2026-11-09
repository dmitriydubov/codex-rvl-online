package ru.projectx.contracts.service.integration.ordering;

import java.util.Optional;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import tools.jackson.databind.JsonNode;

final class OrderingReplies {

    static final String HTTP = "HTTP ";

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int CLIENT_ERRORS = 400;
    private static final int SERVER_ERRORS = 500;
    private static final int SUCCESS_FROM = 200;
    private static final int SUCCESS_TO = 299;
    private static final String STATUS = "status";
    private static final String SUCCESS = "success";
    private static final String ERROR = "error";
    private static final String ERRORS = "errors";
    private static final String CODE = "code";

    private OrderingReplies() {
    }

    static boolean tokenRefused(OrderingReply reply) {
        return reply.status() == UNAUTHORIZED || reply.status() == FORBIDDEN;
    }

    static JsonNode successBody(OrderingOperation operation, OrderingReply reply) {
        if (temporary(reply)) {
            throw new OrderingUnavailableException(operation, HTTP + reply.status());
        }
        if (reply.status() >= CLIENT_ERRORS) {
            throw rejected(operation, reply);
        }
        return bodyOfSuccess(operation, reply);
    }

    static String requiredText(OrderingOperation operation, JsonNode value) {
        return Optional.of(value)
                .filter(JsonNode::isString)
                .map(JsonNode::asString)
                .filter(text -> !text.isBlank())
                .orElseThrow(() -> new OrderingUnavailableException(operation, "в ответе нет обязательного поля"));
    }

    private static boolean temporary(OrderingReply reply) {
        return reply.status() >= SERVER_ERRORS || tokenRefused(reply);
    }

    private static JsonNode bodyOfSuccess(OrderingOperation operation, OrderingReply reply) {
        if (!readable(reply)) {
            throw new OrderingUnavailableException(operation, "ответ не по формату: " + HTTP + reply.status());
        }
        String status = reply.body().path(STATUS).asString("");
        if (ERROR.equals(status)) {
            throw rejected(operation, reply);
        }
        return requireSuccess(operation, status, reply.body());
    }

    private static boolean readable(OrderingReply reply) {
        return reply.status() >= SUCCESS_FROM && reply.status() <= SUCCESS_TO && reply.body().isObject();
    }

    private static JsonNode requireSuccess(OrderingOperation operation, String status, JsonNode body) {
        if (!SUCCESS.equals(status)) {
            throw new OrderingUnavailableException(operation, "ответ без признака успеха");
        }
        return body;
    }

    private static OrderingRejectedException rejected(OrderingOperation operation, OrderingReply reply) {
        return new OrderingRejectedException(operation, errorCode(reply.body()).orElse(HTTP + reply.status()));
    }

    private static Optional<String> errorCode(JsonNode body) {
        return Optional.of(body.path(ERRORS).path(0).path(CODE))
                .filter(JsonNode::isString)
                .map(JsonNode::asString)
                .filter(code -> !code.isBlank());
    }
}
