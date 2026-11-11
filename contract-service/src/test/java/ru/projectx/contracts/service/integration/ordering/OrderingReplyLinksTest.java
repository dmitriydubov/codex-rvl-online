package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingOrderFailureException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.StringNode;

class OrderingReplyLinksTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PREFIX = "https://";

    @ParameterizedTest
    @ValueSource(strings = {"https://pay.example.test/pay/7c1e5a90", "http://pay.example.test/pay?order=7c1e5a90&s=~1",
        "https://p"})
    void httpLinkOfPrintableAsciiCharacters_isReturnedAsItCame(String url) {
        assertThat(OrderingReplies.requiredUrl(OrderingOperation.CHECKOUT, StringNode.valueOf(url))).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "pay.example.test/pay/7c1e5a90", "ftp://pay.example.test/pay", "https://",
        "https://pay.example.test/pay 7c1e5a90", "https://pay.example.test/оплата", "https://pay.example.test/\tpay",
        " https://pay.example.test/pay", "javascript://pay.example.test/pay"})
    void textThatIsNotAnHttpLinkOfPrintableAsciiCharacters_isAFailureOfThisOrderWithoutTheTextItself(String url) {
        assertThatThrownBy(() -> OrderingReplies.requiredUrl(OrderingOperation.CHECKOUT, StringNode.valueOf(url)))
                .isInstanceOfSatisfying(OrderingOrderFailureException.class,
                        failure -> assertThat(failure.operation()).isEqualTo(OrderingOperation.CHECKOUT))
                .hasMessage("сервис управления заказами не обработал заказ: checkout, "
                        + "в ответе нет ссылки или она не по формату");
    }

    @Test
    void linkOfTwoThousandCharactersAfterTheScheme_isTheLongestAccepted() {
        String longest = PREFIX + "a".repeat(2000);

        assertThat(OrderingReplies.requiredUrl(OrderingOperation.CHECKOUT, StringNode.valueOf(longest)))
                .isEqualTo(longest);
        assertThatThrownBy(() -> OrderingReplies.requiredUrl(OrderingOperation.CHECKOUT,
                StringNode.valueOf(longest + "a")))
                .isInstanceOf(OrderingOrderFailureException.class);
    }

    @Test
    void absentOrNonTextLink_isAFailureOfThisOrder() {
        JsonNode number = JSON.readTree("7");
        JsonNode object = JSON.readTree("{\"url\": \"https://pay.example.test/pay\"}");

        assertThatThrownBy(() -> OrderingReplies.requiredUrl(OrderingOperation.CHECKOUT, MissingNode.getInstance()))
                .isInstanceOf(OrderingOrderFailureException.class);
        assertThatThrownBy(() -> OrderingReplies.requiredUrl(OrderingOperation.CHECKOUT, number))
                .isInstanceOf(OrderingOrderFailureException.class);
        assertThatThrownBy(() -> OrderingReplies.requiredUrl(OrderingOperation.CHECKOUT, object))
                .isInstanceOf(OrderingOrderFailureException.class);
        assertThatThrownBy(() -> OrderingReplies.requiredUrl(OrderingOperation.CHECKOUT, JSON.nullNode()))
                .isInstanceOf(OrderingOrderFailureException.class);
    }
}
