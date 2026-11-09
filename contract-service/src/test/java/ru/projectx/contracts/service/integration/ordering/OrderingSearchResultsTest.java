package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
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

    @Test
    void noData_orEmptyList_findsNothing() {
        assertThat(OrderingSearchResults.orderId(JSON.readTree("{\"status\": \"success\"}"), "rvl", DOCUMENT))
                .isEmpty();
        assertThat(OrderingSearchResults.orderId(JSON.readTree("{\"data\": []}"), "rvl", DOCUMENT)).isEmpty();
        assertThat(OrderingSearchResults.orderId(JSON.readTree("{\"data\": \"none\"}"), "rvl", DOCUMENT)).isEmpty();
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
    void ourDocumentWithoutUsableOrderId_findsNothing() {
        String blank = "{\"data\": {\"orderId\": \" \", \"orderDetails\": [{\"product\": \"rvl\", \"documentId\": \""
                + DOCUMENT + "\"}]}}";
        String numeric = blank.replace("\" \"", "5");

        assertThat(OrderingSearchResults.orderId(JSON.readTree(blank), "rvl", DOCUMENT)).isEmpty();
        assertThat(OrderingSearchResults.orderId(JSON.readTree(numeric), "rvl", DOCUMENT)).isEmpty();
    }
}
