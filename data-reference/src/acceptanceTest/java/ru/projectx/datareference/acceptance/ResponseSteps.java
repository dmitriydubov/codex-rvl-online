package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.util.Set;
import java.util.regex.Pattern;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

public class ResponseSteps {

    private static final String NULL = "null";
    private static final Set<String> JSON_LITERALS = Set.of(NULL, "true", "false");
    private static final Pattern ISSUED_UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern FIELD_LIST = Pattern.compile("\\s*,\\s*");

    private final ContractsService contracts;

    public ResponseSteps(ContractsService contracts) {
        this.contracts = contracts;
    }

    @Тогда("Дата-референс отвечает {int}")
    public void respondsWith(int status) {
        ServiceResponse response = contracts.lastResponse();
        assertThat(response.status()).as("статус ответа: %s", response.body()).isEqualTo(status);
    }

    @Тогда("поле {string} ответа равно {string}")
    public void fieldEquals(String field, String expected) {
        JsonNode actual = presentField(field);
        if (actual.isNumber()) {
            assertThat(actual.decimalValue()).as("поле %s", field).isEqualByComparingTo(expected);
            return;
        }
        assertThat(actual.isValueNode()).as("поле %s — значение, а не объект или список: %s", field, actual).isTrue();
        assertThat(actual.isString())
                .as("поле %s — строка JSON, если ожидается не null/true/false: %s", field, actual)
                .isEqualTo(!JSON_LITERALS.contains(expected));
        assertThat(actual.isNull() ? NULL : actual.asString()).as("поле %s", field).isEqualTo(expected);
    }

    @Тогда("поле {string} ответа заполнено")
    public void fieldFilled(String field) {
        assertThat(presentString(field)).as("поле %s", field).isNotBlank();
    }

    @Тогда("поле {string} ответа — пустой список")
    public void fieldIsEmptyList(String field) {
        JsonNode actual = presentField(field);
        assertThat(actual.isArray() && actual.isEmpty()).as("поле %s — пустой список: %s", field, actual).isTrue();
    }

    @Тогда("поля {string} в ответе нет")
    public void fieldAbsent(String field) {
        JsonNode body = objectBody();
        assertThat(contracts.lastResponse().field(field).isMissingNode())
                .as("поля %s нет в ответе: %s", field, body)
                .isTrue();
    }

    @Тогда("ответ состоит ровно из полей {string}")
    public void responseHasExactlyFields(String fields) {
        JsonNode body = objectBody();
        assertThat(body.propertyNames())
                .as("поля верхнего уровня ответа: %s", body)
                .containsExactlyInAnyOrder(FIELD_LIST.split(fields.strip()));
    }

    @Тогда("поле {string} ответа равно полученному id")
    public void fieldEqualsObtainedId(String field) {
        assertThat(presentString(field)).as("поле %s", field).isEqualTo(contracts.obtainedId());
    }

    @Тогда("заголовок X-Request-Id ответа равен {string}")
    public void requestIdEquals(String expected) {
        assertThat(contracts.lastResponse().requestIds()).as("заголовок X-Request-Id").containsExactly(expected);
    }

    @Тогда("заголовка Retry-After в ответе нет")
    public void noRetryAfter() {
        assertThat(contracts.lastResponse().headers().getOrEmpty(HttpHeaders.RETRY_AFTER))
                .as("заголовки Retry-After")
                .isEmpty();
    }

    @Тогда("заголовок X-Request-Id ответа заполнен")
    public void requestIdFilled() {
        soleRequestId();
    }

    @Тогда("заголовок X-Request-Id ответа — новый UUID")
    public void requestIdIsIssuedUuid() {
        assertThat(soleRequestId()).as("заголовок X-Request-Id").matches(ISSUED_UUID);
    }

    @Тогда("поле {string} ответа равно заголовку X-Request-Id ответа")
    public void fieldEqualsRequestId(String field) {
        assertThat(presentString(field)).as("поле %s", field).isEqualTo(soleRequestId());
    }

    private String soleRequestId() {
        return assertThat(contracts.lastResponse().requestIds())
                .as("заголовок X-Request-Id — ровно один")
                .singleElement(InstanceOfAssertFactories.STRING)
                .isNotBlank()
                .actual();
    }

    private JsonNode objectBody() {
        JsonNode body = contracts.lastResponse().body();
        assertThat(body.isObject()).as("тело ответа — объект JSON: %s", body).isTrue();
        return body;
    }

    private String presentString(String field) {
        JsonNode actual = presentField(field);
        assertThat(actual.isString()).as("поле %s — строка: %s", field, actual).isTrue();
        return actual.asString();
    }

    private JsonNode presentField(String field) {
        ServiceResponse response = contracts.lastResponse();
        JsonNode actual = response.field(field);
        assertThat(actual.isMissingNode()).as("поля %s нет в ответе: %s", field, response.body()).isFalse();
        return actual;
    }
}
