package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.GetResponse;
import io.cucumber.java.ru.Тогда;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public class StatusMessageSteps {

    private static final Set<String> ENVELOPE = Set.of("topic", "event_type", "schema_version", "source",
            "correlation_id", "entity_version", "created_at");
    private static final Set<String> PARTICIPANT = Set.of("участник", "document_kind", "km_contract_state",
            "km_contract_id", "km_contract_number", "payment_status", "documents", "error_code", "error_message");
    private static final String KM_CONTRACT = "договор КМ";

    private final ReceivedStatusMessages received;
    private final TaskMessages messages;
    private final PublishedMessages published;
    private final OutboxRecords journal;
    private final IssuanceRecords records;

    public StatusMessageSteps(ReceivedStatusMessages received, TaskMessages messages, PublishedMessages published,
            OutboxRecords journal, IssuanceRecords records) {
        this.received = received;
        this.messages = messages;
        this.published = published;
        this.journal = journal;
        this.records = records;
    }

    @Тогда("тело сообщения о состоянии выпуска №{int} совпадает с телом исходящего сообщения №{int} по заявке {string} в журнале")
    public void bodyMatchesTheJournal(int receivedOrdinal, int ordinal, String requestId) {
        String payload = journal.number(ordinal, requestId).payload();
        assertThat(payload).as("тело записи №%s в журнале исходящих", ordinal).isNotNull();
        assertThat(messages.read(received.number(receivedOrdinal).getBody()))
                .isEqualTo(messages.read(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Тогда("сообщение о состоянии выпуска №{int} получено с конвертом:")
    public void envelope(int ordinal, Map<String, String> expected) {
        StepTables.onlyKnown(expected, ENVELOPE);
        JsonNode meta = body(ordinal).path("meta");
        SoftAssertions softly = new SoftAssertions();
        for (Map.Entry<String, String> cell : expected.entrySet()) {
            if ("created_at".equals(cell.getKey())) {
                softly.assertThat(OffsetDateTime.parse(meta.path("created_at").stringValue()).toInstant())
                        .as("created_at").isEqualTo(OffsetDateTime.parse(cell.getValue()).toInstant());
            }
            else {
                softly.assertThat(meta.path(cell.getKey()).asString()).as(cell.getKey()).isEqualTo(cell.getValue());
            }
        }
        softly.assertAll();
    }

    @Тогда("сообщение о состоянии выпуска №{int}: заявка {string}, статус {string}, участники:")
    public void state(int ordinal, String requestId, String status, List<Map<String, String>> participants) {
        JsonNode data = body(ordinal).path("data");
        assertThat(data.path("individual_request_id").stringValue()).isEqualTo(requestId);
        assertThat(data.path("status").stringValue()).isEqualTo(status);
        assertThat(data.path("participants").isArray()).as("participants — массив").isTrue();
        assertThat(data.path("participants")).as("число участников в ответе").hasSize(participants.size());
        SoftAssertions softly = new SoftAssertions();
        for (int index = 0; index < participants.size(); index++) {
            participant(softly, participants.get(index), data.path("participants").path(index), requestId);
        }
        softly.assertAll();
    }

    @Тогда("значение поля {string} сообщения {string} есть в его теле, но не попало в сообщение о состоянии выпуска №{int}")
    public void personalValueStaysOutOfTheStatus(String field, String messageId, int ordinal) {
        JsonNode task = messages.read(published.of(messageId));
        String value = ServiceLogSteps.value(task, field)
                .orElseThrow(() -> new AssertionError("в опубликованной задаче нет поля " + field));
        GetResponse message = received.number(ordinal);
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).as("полученное сообщение").doesNotContain(value);
        Optional<OutboxRecords.Entry> entry = journal.all().stream()
                .filter(candidate -> candidate.messageId().toString().equals(message.getProps().getMessageId()))
                .findFirst();
        assertThat(entry).as("запись журнала исходящих для полученного сообщения").isPresent();
        assertThat(entry.get().payload()).satisfiesAnyOf(
                payload -> assertThat(payload).isNull(),
                payload -> assertThat(payload).doesNotContain(value));
    }

    private void participant(SoftAssertions softly, Map<String, String> expected, JsonNode actual, String requestId) {
        StepTables.onlyKnown(expected, PARTICIPANT);
        String participantId = StepTables.required(expected, "участник");
        softly.assertThat(actual.path("id").stringValue()).as("id").isEqualTo(participantId);
        for (Map.Entry<String, String> cell : expected.entrySet()) {
            if (!"участник".equals(cell.getKey())) {
                field(softly, cell.getKey(), cell.getValue(), actual, participantId, requestId);
            }
        }
    }

    private void field(SoftAssertions softly, String name, @Nullable String expected, JsonNode actual,
            String participantId, String requestId) {
        if (expected == null || expected.isBlank()) {
            softly.assertThat(actual.has(name)).as("поле %s отсутствует", name).isFalse();
        }
        else if ("documents".equals(name)) {
            assertThat(expected).as("ожидание о documents").isEqualTo("нет");
            softly.assertThat(actual.path(name).isArray() && actual.path(name).isEmpty()).as("documents пуст").isTrue();
        }
        else if ("km_contract_id".equals(name)) {
            assertThat(expected).as("ожидание о km_contract_id").isEqualTo("есть");
            softly.assertThat(actual.path(name).stringValue()).as(name)
                    .isEqualTo(latestContractId(participantId, requestId).toString());
        }
        else {
            softly.assertThat(actual.path(name).asString()).as(name).isEqualTo(expected);
        }
    }

    private UUID latestContractId(String participantId, String requestId) {
        List<IssuanceRecords.Record> contracts = records.of(participantId, requestId).stream()
                .filter(record -> KM_CONTRACT.equals(record.document())).toList();
        assertThat(contracts).as("договоры КМ участника %s", participantId).isNotEmpty();
        return contracts.get(contracts.size() - 1).id();
    }

    private JsonNode body(int ordinal) {
        return messages.read(received.number(ordinal).getBody());
    }
}
