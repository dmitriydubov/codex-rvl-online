package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.util.Set;
import java.util.regex.Pattern;
import org.assertj.core.api.InstanceOfAssertFactories;
import tools.jackson.databind.JsonNode;

/**
 * Проверки ответа Дата-референса: статус, поля тела JSON и заголовок {@code X-Request-Id}.
 *
 * <p>Значение поля сравнивается с учётом его типа в JSON: число — по величине ({@code 10.00}
 * и {@code 10.0} равны), причём тип «число» сам не проверяется — строка {@code "1"}
 * совпала бы с ожиданием «1» (спецификация типов полей не называет); слова «null», «true», «false» — только с JSON-литералом
 * ({@code null}, логическим значением), а не со строкой из тех же букв; прочее — только
 * со строкой JSON. Объект или список не равен ничему. Отсутствующее поле не равно ничему,
 * в том числе «null».
 *
 * <p>Заголовок {@code X-Request-Id} ожидается ровно один: второй значением не считается.
 */
public class ResponseSteps {

    private static final String NULL = "null";
    private static final Set<String> JSON_LITERALS = Set.of(NULL, "true", "false");
    /** UUID, каким его выдаёт сервис: 8-4-4-4-12 шестнадцатеричных цифр в нижнем регистре. */
    private static final Pattern ISSUED_UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    /** Перечень полей в шаге: имена через запятую, пробелы вокруг запятой не значимы. */
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

    /**
     * Поля нет вовсе — не «есть и пусто»: {@code null} в ответе тоже раскрыл бы, что поле существует.
     * Тело при этом — объект JSON: в пустом теле или списке путь тоже ничего не находит, и проверка
     * прошла бы вхолостую.
     */
    @Тогда("поля {string} в ответе нет")
    public void fieldAbsent(String field) {
        JsonNode body = objectBody();
        assertThat(contracts.lastResponse().field(field).isMissingNode())
                .as("поля %s нет в ответе: %s", field, body)
                .isTrue();
    }

    /**
     * Поля верхнего уровня тела — ровно названные, в любом порядке: лишнее поле (в том числе
     * {@code null}) проваливает проверку так же, как недостающее.
     */
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

    @Тогда("заголовок X-Request-Id ответа заполнен")
    public void requestIdFilled() {
        soleRequestId();
    }

    /**
     * Сервис выдал свой идентификатор: ровно один заголовок, и в нём UUID. Присланное значение,
     * возвращённое как есть, эту проверку не проходит: недопустимые значения сценариев на UUID
     * не похожи (см. request_id.feature).
     */
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
