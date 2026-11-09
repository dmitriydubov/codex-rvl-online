package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingOrderFailureException;
import tools.jackson.databind.json.JsonMapper;

class OrderingSearchResultsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DOCUMENT = "0196f0a2-0000-7000-8000-000000000011";
    private static final String ORDER = """
            {"orderId": "o-1", "status": "NEW", "orderDetails": [
              {"product": "other", "documentId": "%1$s"},
              {"product": "rvl", "documentId": "%1$s"}]}""".formatted(DOCUMENT);

    @Test
    void orderAsObject_isFoundByProductAndDocument() {
        assertThat(OrderingSearchResults.orderId(JSON.readTree("{\"data\": " + ORDER + "}"), "rvl", DOCUMENT))
                .contains("o-1");
    }

    @Test
    void orderInAList_isFoundAmongOthers() {
        String other = "{\"orderId\": \"o-0\", \"orderDetails\": [{\"product\": \"rvl\", \"documentId\": \"x\"}]}";

        assertThat(OrderingSearchResults.orderId(
                JSON.readTree("{\"data\": [" + other + ", " + ORDER + "]}"), "rvl", DOCUMENT)).contains("o-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\": \"success\"}", "{\"data\": null}", "{\"data\": []}"})
    void noData_nullData_orEmptyList_findsNothing(String body) {
        assertThat(OrderingSearchResults.orderId(JSON.readTree(body), "rvl", DOCUMENT)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"data\": \"none\"}", "{\"data\": 5}", "{\"data\": true}"})
    void dataNeitherObjectNorListNorNull_isAFailureOfThisOrder(String body) {
        assertThatThrownBy(() -> OrderingSearchResults.orderId(JSON.readTree(body), "rvl", DOCUMENT))
                .isInstanceOfSatisfying(OrderingOrderFailureException.class,
                        failure -> assertThat(failure.operation()).isEqualTo(OrderingOperation.FIND_ORDERS));
    }

    @Test
    void sameDocumentOfAnotherProduct_isNotOurs() {
        assertThat(OrderingSearchResults.orderId(JSON.readTree("{\"data\": " + ORDER + "}"), "casco", DOCUMENT))
                .isEmpty();
    }

    @Test
    void anotherDocument_isNotOurs() {
        assertThat(OrderingSearchResults.orderId(JSON.readTree("{\"data\": " + ORDER + "}"), "rvl", "other"))
                .isEmpty();
    }

    @Test
    void orderIdLongerThanTheColumn_isNotTaken() {
        String body = "{\"data\": {\"orderId\": \"%s\", \"orderDetails\": [{\"product\": \"rvl\", "
                + "\"documentId\": \"" + DOCUMENT + "\"}]}}";

        assertThatThrownBy(() -> OrderingSearchResults.orderId(JSON.readTree(body.formatted("o".repeat(101))), "rvl",
                DOCUMENT)).isInstanceOf(OrderingOrderFailureException.class);
        assertThat(OrderingSearchResults.orderId(JSON.readTree(body.formatted("o".repeat(100))), "rvl", DOCUMENT))
                .contains("o".repeat(100));
    }

    @Test
    void ourDocumentWithoutUsableOrderId_isAFailureOfThisOrder() {
        String blank = "{\"data\": {\"orderId\": \" \", \"orderDetails\": [{\"product\": \"rvl\", \"documentId\": \""
                + DOCUMENT + "\"}]}}";
        String numeric = blank.replace("\" \"", "5");

        assertThatThrownBy(() -> OrderingSearchResults.orderId(JSON.readTree(blank), "rvl", DOCUMENT))
                .isInstanceOfSatisfying(OrderingOrderFailureException.class,
                        failure -> assertThat(failure.operation()).isEqualTo(OrderingOperation.FIND_ORDERS));
        assertThatThrownBy(() -> OrderingSearchResults.orderId(JSON.readTree(numeric), "rvl", DOCUMENT))
                .isInstanceOf(OrderingOrderFailureException.class);
        assertThatThrownBy(() -> OrderingSearchResults.orderId(JSON.readTree(blank.replace("\"orderId\": \" \", ", "")),
                "rvl", DOCUMENT)).isInstanceOf(OrderingOrderFailureException.class);
    }
}
