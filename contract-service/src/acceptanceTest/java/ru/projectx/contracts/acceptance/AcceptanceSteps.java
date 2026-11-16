package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.assertj.core.api.SoftAssertions;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class AcceptanceSteps {

    private static final String CONTRACT_VERSION = "contract_version";
    private static final String ACCEPTED_AT = "accepted_at";
    private static final String CREATED_AT = "created_at";
    private static final String SAME_AS_PATH = "как в пути";
    private static final String ABSENT = "нет";
    private static final String NOT_A_GUID = "not-a-guid";
    private static final String EMAIL = "email";
    private static final String STAND_EMAIL = "insured@example.test";
    private static final String TOO_LONG_EMAIL = "payer-" + "a".repeat(58) + "@stand-" + "b".repeat(55) + ".stand-"
            + "c".repeat(55) + ".stand-" + "d".repeat(55) + ".test";
    private static final int TOO_LONG = 255;
    private static final String NOT_JSON = "{\"contract_version\": 1, \"channel\": \"WEB\"";
    private static final Map<String, UnaryOperator<ObjectNode>> DEFECTS = Map.ofEntries(
            Map.entry("нет поля contract_version", body -> body.without(CONTRACT_VERSION)),
            Map.entry("нет поля accepted_by", body -> body.without("accepted_by")),
            Map.entry("нет поля accepted_at", body -> body.without(ACCEPTED_AT)),
            Map.entry("нет поля channel", body -> body.without("channel")),
            Map.entry("contract_version — строка", body -> body.put(CONTRACT_VERSION, "один")),
            Map.entry("accepted_by — не GUID", body -> body.put("accepted_by", NOT_A_GUID)),
            Map.entry("accepted_at без часового пояса", body -> body.put(ACCEPTED_AT, "2026-02-10T10:04:58")),
            Map.entry("accepted_at — не дата и время", body -> body.put(ACCEPTED_AT, "вчера")),
            Map.entry("channel вне перечня", body -> body.put("channel", "SMS")),
            Map.entry("ip_address — не IP-адрес", body -> body.put("ip_address", "999.1.1.1")),
            Map.entry("нет поля email", body -> body.without(EMAIL)),
            Map.entry("email — пустая строка", body -> body.put(EMAIL, "")),
            Map.entry("email — число", body -> body.put(EMAIL, 42)),
            Map.entry("email без @", body -> body.put(EMAIL, "insured.example.test")),
            Map.entry("email без домена", body -> body.put(EMAIL, "insured@")),
            Map.entry("email с пробелом", body -> body.put(EMAIL, "insured @example.test")),
            Map.entry("email с кириллицей в имени", body -> body.put(EMAIL, "страхователь@example.test")),
            Map.entry("email с кириллицей в домене", body -> body.put(EMAIL, "insured@пример.тест")),
            Map.entry("email с доменом без точки", body -> body.put(EMAIL, "insured@localhost")),
            Map.entry("email в кавычках", body -> body.put(EMAIL, "\"insured\"@example.test")),
            Map.entry("email с IP-адресом вместо домена", body -> body.put(EMAIL, "insured@[192.0.2.1]")),
            Map.entry("email длиннее 254 символов", body -> body.put(EMAIL, TOO_LONG_EMAIL)),
            Map.entry("идентификатор договора в пути — не GUID", UnaryOperator.identity()),
            Map.entry("тело не JSON", UnaryOperator.identity()));

    private final AttachmentCalls calls;
    private final IssuanceRecords records;
    private final AcceptanceRecords acceptances;
    private final ServiceJournal journal;
    private final JsonMapper json = JsonMapper.builder().build();
    private String lastContract = "";

    public AcceptanceSteps(AttachmentCalls calls, IssuanceRecords records, AcceptanceRecords acceptances,
            ServiceJournal journal) {
        this.calls = calls;
        this.records = records;
        this.acceptances = acceptances;
        this.journal = journal;
    }

    @Когда("сервис Заявок передаёт акцепт договора КМ участника {string} заявки {string}:")
    public void acceptsContractOf(String participant, String request, Map<String, String> fields)
            throws IOException, InterruptedException {
        accept(records.latestContractId(participant, request).toString(), Optional.empty(), body(fields));
    }

    @Когда("сервис Заявок передаёт акцепт договора КМ участника {string} заявки {string} с заголовком X-Request-Id {string}:")
    public void acceptsContractOfWithRequestId(String participant, String request, String requestId,
            Map<String, String> fields) throws IOException, InterruptedException {
        accept(records.latestContractId(participant, request).toString(), Optional.of(requestId), body(fields));
    }

    @Когда("сервис Заявок передаёт акцепт договора КМ {string}:")
    public void acceptsContract(String contract, Map<String, String> fields) throws IOException, InterruptedException {
        accept(contract, Optional.empty(), body(fields));
    }

    @Когда("сервис Заявок передаёт акцепт договора КМ участника {string} заявки {string} с дефектом {string}")
    public void acceptsWithDefect(String participant, String request, String defect)
            throws IOException, InterruptedException {
        UnaryOperator<ObjectNode> change = Optional.ofNullable(DEFECTS.get(defect))
                .orElseThrow(() -> new AssertionError("неизвестный дефект запроса акцепта: " + defect));
        assertThat(TOO_LONG_EMAIL).as("адрес стенда длиннее предела").hasSize(TOO_LONG);
        String contract = defect.startsWith("идентификатор договора") ? NOT_A_GUID
                : records.latestContractId(participant, request).toString();
        String body = "тело не JSON".equals(defect) ? NOT_JSON : change.apply(intactBody()).toString();
        accept(contract, Optional.empty(), body);
    }

    @Тогда("Договоры ответили на акцепт {int}:")
    public void answeredAcceptance(int status, Map<String, String> fields) {
        AttachmentCalls.Answer answer = calls.last();
        assertThat(answer.status()).as("код ответа, тело %s", answer.body()).isEqualTo(status);
        JsonNode body = calls.body(answer);
        SoftAssertions softly = new SoftAssertions();
        fields.forEach((name, expected) -> answerField(softly, body, name, expected));
        softly.assertAll();
    }

    @Тогда("акцепт договора КМ участника {string} заявки {string}:")
    public void acceptanceRecord(String participant, String request, Map<String, String> fields) {
        List<AcceptanceRecords.Acceptance> all = acceptances.of(records.latestContractId(participant, request));
        assertThat(all).as("записи об акцепте договора КМ участника %s", participant).hasSize(1);
        AcceptanceRecords.Acceptance acceptance = all.get(0);
        SoftAssertions softly = new SoftAssertions();
        fields.forEach((name, expected) -> AcceptanceFields.check(softly, acceptance, name, expected));
        softly.assertAll();
    }

    @Тогда("записей об акцепте договора КМ участника {string} заявки {string} — {int}")
    public void acceptanceCount(String participant, String request, int count) {
        assertThat(acceptances.of(records.latestContractId(participant, request))).hasSize(count);
    }

    @Тогда("в ошибке одно нарушение — по полю {string} с причиной {string}")
    public void oneViolationWithReason(String field, String reason) {
        JsonNode details = calls.body(calls.last()).path("error").path("details");
        assertThat(details.size()).as("число нарушений в %s", details).isEqualTo(1);
        assertThat(details.path(0).path("field").asString()).isEqualTo(field);
        assertThat(details.path(0).path("reason").asString()).isEqualTo(reason);
    }

    @Тогда("IP-адрес {string} и клиент {string} принявшего оферту не попали в журнал Договоров")
    public void addressAndClientStayOutOfTheJournal(String address, String client) {
        assertThat(address).as("IP-адрес").isNotBlank();
        assertThat(client).as("клиент").isNotBlank();
        assertThat(journal.events()).as("журнал за сценарий").isNotEmpty();
        assertThat(journal.mentioning(address)).as("записи журнала с адресом").isEmpty();
        assertThat(journal.mentioning(client)).as("записи журнала с клиентом").isEmpty();
    }

    private void accept(String contract, Optional<String> requestId, String body)
            throws IOException, InterruptedException {
        lastContract = contract;
        calls.exchange("POST", "/internal/v1/contracts/" + contract + "/acceptance", requestId, Optional.of(body));
    }

    private String body(Map<String, String> fields) {
        ObjectNode body = json.createObjectNode();
        fields.forEach((name, value) -> put(body, name, value));
        return body.toString();
    }

    private static void put(ObjectNode body, String name, @Nullable String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (value.chars().allMatch(Character::isDigit)) {
            body.put(name, Integer.parseInt(value));
            return;
        }
        body.put(name, value);
    }

    private ObjectNode intactBody() {
        ObjectNode body = json.createObjectNode();
        body.put(CONTRACT_VERSION, 1);
        body.put("accepted_by", "b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45");
        body.put(ACCEPTED_AT, "2026-02-10T10:04:58+03:00");
        body.put("channel", "WEB");
        body.put(EMAIL, STAND_EMAIL);
        body.put("ip_address", "10.20.30.40");
        body.put("user_agent", "Mozilla/5.0 (Стенд; вымышленный браузер)");
        return body;
    }

    private void answerField(SoftAssertions softly, JsonNode body, String name, String expected) {
        JsonNode actual = body.path(name);
        if (ABSENT.equals(expected)) {
            softly.assertThat(actual.isMissingNode() || actual.isNull()).as("поля %s нет в %s", name, body).isTrue();
            return;
        }
        if (SAME_AS_PATH.equals(expected)) {
            softly.assertThat(actual.asString()).as("поле %s", name).isEqualTo(UUID.fromString(lastContract).toString());
            return;
        }
        if (ACCEPTED_AT.equals(name) || CREATED_AT.equals(name)) {
            softly.assertThat(OffsetDateTime.parse(actual.asString()).toInstant()).as("поле %s", name)
                    .isEqualTo(OffsetDateTime.parse(expected).toInstant());
            return;
        }
        softly.assertThat(actual.asString()).as("поле %s", name).isEqualTo(expected);
    }
}
