package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

class OrderingRepliesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ERROR_BODY = """
            {"status": "error", "errors": [{"index": 0, "code": "CreateOrderValidatorService.valid",
             "message": "$.clientId: значение Стенд-1", "isCritical": true}]}""";

    private static OrderingReply reply(int status, String body) {
        return new OrderingReply(status, JSON.readTree(body));
    }

    @Test
    void successBody_isReturned() {
        JsonNode body = OrderingReplies.successBody(OrderingOperation.CREATE_ORDER,
                reply(200, "{\"status\": \"success\", \"orderId\": \"o-1\"}"));

        assertThat(body.path("orderId").asString()).isEqualTo("o-1");
    }

    @Test
    void errorStatusWith200_isRejectedWithTheCodeOnly() {
        assertThatThrownBy(() -> OrderingReplies.successBody(OrderingOperation.CREATE_ORDER, reply(200, ERROR_BODY)))
                .isInstanceOfSatisfying(OrderingRejectedException.class, rejected -> {
                    assertThat(rejected.code()).isEqualTo("CreateOrderValidatorService.valid");
                    assertThat(rejected.operation()).isEqualTo(OrderingOperation.CREATE_ORDER);
                })
                .message().doesNotContain("Стенд-1");
    }

    @Test
    void clientError_isRejectedWithTheCodeOfTheBody() {
        assertThatThrownBy(() -> OrderingReplies.successBody(OrderingOperation.CANCEL_ORDER, reply(400, ERROR_BODY)))
                .isInstanceOfSatisfying(OrderingRejectedException.class,
                        rejected -> assertThat(rejected.code()).isEqualTo("CreateOrderValidatorService.valid"));
    }

    @Test
    void clientErrorWithoutBody_isRejectedWithTheHttpStatusAsCode() {
        OrderingReply noBody = new OrderingReply(422, MissingNode.getInstance());

        assertThatThrownBy(() -> OrderingReplies.successBody(OrderingOperation.CREATE_ORDER, noBody))
                .isInstanceOfSatisfying(OrderingRejectedException.class,
                        rejected -> assertThat(rejected.code()).isEqualTo("HTTP 422"));
    }

    @Test
    void clientErrorWithBlankCode_isRejectedWithTheHttpStatusAsCode() {
        assertThatThrownBy(() -> OrderingReplies.successBody(OrderingOperation.CREATE_ORDER,
                reply(409, "{\"status\": \"error\", \"errors\": [{\"code\": \" \"}]}")))
                .isInstanceOfSatisfying(OrderingRejectedException.class,
                        rejected -> assertThat(rejected.code()).isEqualTo("HTTP 409"));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 401, 403})
    void serverErrorsAndRefusedToken_areTemporary(int status) {
        assertThatThrownBy(() -> OrderingReplies.successBody(OrderingOperation.FIND_ORDERS, reply(status, ERROR_BODY)))
                .isInstanceOfSatisfying(OrderingUnavailableException.class,
                        unavailable -> assertThat(unavailable.operation()).isEqualTo(OrderingOperation.FIND_ORDERS))
                .hasMessageContaining("HTTP " + status);
    }

    @Test
    void lastClientError_isRejectedAndFirstServerErrorIsTemporary() {
        assertThatThrownBy(() -> OrderingReplies.successBody(OrderingOperation.CREATE_ORDER, reply(499, ERROR_BODY)))
                .isInstanceOf(OrderingRejectedException.class);
        assertThatThrownBy(() -> OrderingReplies.successBody(OrderingOperation.CREATE_ORDER, reply(500, "{}")))
                .isInstanceOf(OrderingUnavailableException.class);
    }

    @Test
    void successWithoutBody_isTemporary() {
        assertThatThrownBy(() -> OrderingReplies.successBody(OrderingOperation.CREATE_ORDER,
                new OrderingReply(200, MissingNode.getInstance())))
                .isInstanceOf(OrderingUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {199, 300})
    void statusOutsideSuccess_withSuccessBody_isTemporary(int status) {
        assertThatThrownBy(() -> OrderingReplies.successBody(OrderingOperation.CREATE_ORDER,
                reply(status, "{\"status\": \"success\"}")))
                .isInstanceOf(OrderingUnavailableException.class);
    }

    @Test
    void lastSuccessStatus_isAccepted() {
        assertThat(OrderingReplies.successBody(OrderingOperation.CREATE_ORDER, reply(299, "{\"status\": \"success\"}"))
                .path("status").asString()).isEqualTo("success");
    }

    @Test
    void successBodyWithoutSuccessMark_isTemporary() {
        assertThatThrownBy(() -> OrderingReplies.successBody(OrderingOperation.CREATE_ORDER,
                reply(200, "{\"orderId\": \"o-1\"}")))
                .isInstanceOf(OrderingUnavailableException.class);
    }

    @Test
    void successArray_isTemporary() {
        assertThatThrownBy(() -> OrderingReplies.successBody(OrderingOperation.CREATE_ORDER, reply(200, "[]")))
                .isInstanceOf(OrderingUnavailableException.class);
    }

    @Test
    void tokenRefused_isOnly401And403() {
        assertThat(OrderingReplies.tokenRefused(new OrderingReply(401, MissingNode.getInstance()))).isTrue();
        assertThat(OrderingReplies.tokenRefused(new OrderingReply(403, MissingNode.getInstance()))).isTrue();
        assertThat(OrderingReplies.tokenRefused(new OrderingReply(400, MissingNode.getInstance()))).isFalse();
        assertThat(OrderingReplies.tokenRefused(new OrderingReply(404, MissingNode.getInstance()))).isFalse();
    }

    @Test
    void requiredText_isTheTextOrTemporaryFailure() {
        assertThat(OrderingReplies.requiredText(OrderingOperation.CREATE_ORDER, JSON.readTree("\"o-1\"")))
                .isEqualTo("o-1");
        assertThatThrownBy(() -> OrderingReplies.requiredText(OrderingOperation.CREATE_ORDER, JSON.readTree("\" \"")))
                .isInstanceOf(OrderingUnavailableException.class);
        assertThatThrownBy(() -> OrderingReplies.requiredText(OrderingOperation.ORDER_STATE, JSON.readTree("7")))
                .isInstanceOfSatisfying(OrderingUnavailableException.class,
                        unavailable -> assertThat(unavailable.operation()).isEqualTo(OrderingOperation.ORDER_STATE));
        assertThatThrownBy(() -> OrderingReplies.requiredText(OrderingOperation.CREATE_ORDER,
                MissingNode.getInstance()))
                .isInstanceOf(OrderingUnavailableException.class);
    }
}
