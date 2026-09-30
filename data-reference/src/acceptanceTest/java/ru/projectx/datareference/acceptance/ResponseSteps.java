package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import tools.jackson.databind.JsonNode;

/**
 * Проверки ответа Дата-референса: статус, поля тела JSON и заголовок {@code X-Request-Id}.
 *
 * <p>Значение поля сравнивается по его типу в JSON: число — по величине ({@code 10.00}
 * и {@code 10.0} равны), {@code null} — только со словом «null», прочее — как текст.
 * Отсутствующее поле не равно ничему, в том числе «null».
 */
public class ResponseSteps {

    private static final String NULL = "null";

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
        assertThat(actual.isNull() ? NULL : actual.asString()).as("поле %s", field).isEqualTo(expected);
    }

    @Тогда("поле {string} ответа заполнено")
    public void fieldFilled(String field) {
        JsonNode actual = presentField(field);
        assertThat(actual.isString()).as("поле %s — строка: %s", field, actual).isTrue();
        assertThat(actual.asString()).as("поле %s", field).isNotBlank();
    }

    @Тогда("поле {string} ответа — пустой список")
    public void fieldIsEmptyList(String field) {
        JsonNode actual = presentField(field);
        assertThat(actual.isArray() && actual.isEmpty()).as("поле %s — пустой список: %s", field, actual).isTrue();
    }

    @Тогда("поле {string} ответа равно полученному id")
    public void fieldEqualsObtainedId(String field) {
        assertThat(presentField(field).asString()).as("поле %s", field).isEqualTo(contracts.obtainedId());
    }

    @Тогда("заголовок X-Request-Id ответа равен {string}")
    public void requestIdEquals(String expected) {
        assertThat(contracts.lastResponse().requestId()).as("заголовок X-Request-Id").hasValue(expected);
    }

    @Тогда("заголовок X-Request-Id ответа заполнен")
    public void requestIdFilled() {
        assertThat(contracts.lastResponse().requestId())
                .as("заголовок X-Request-Id")
                .hasValueSatisfying(requestId -> assertThat(requestId).isNotBlank());
    }

    @Тогда("поле {string} ответа равно заголовку X-Request-Id ответа")
    public void fieldEqualsRequestId(String field) {
        String header = contracts.lastResponse().requestId()
                .orElseThrow(() -> new AssertionError("в ответе нет заголовка X-Request-Id"));
        assertThat(presentField(field).asString()).as("поле %s", field).isEqualTo(header);
    }

    private JsonNode presentField(String field) {
        ServiceResponse response = contracts.lastResponse();
        JsonNode actual = response.field(field);
        assertThat(actual.isMissingNode()).as("поля %s нет в ответе: %s", field, response.body()).isFalse();
        return actual;
    }
}
