package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.ContractViolationException;
import tools.jackson.databind.json.JsonMapper;

class JsonFieldsTest {

    private static final String BODY = """
            {"data": {"id": "61a7982c-a0b4-4bbf-9527-60b40b752803", "date": "2026-04-10", "amount": 34000.00,
                      "flag": true, "name": "Тестов", "number": 5, "items": [{"n": 1}], "nested": {}}}
            """;

    private final JsonFields root = new JsonFields(JsonMapper.builder().build().readTree(BODY), "");
    private final JsonFields data = root.requiredObject("data");

    @Test
    void paths_startAtRootWithoutLeadingDot() {
        assertThat(data.path()).isEqualTo("data");
        assertThat(data.child("name").path()).isEqualTo("data.name");
        assertThat(data.requiredArray("items").item(0).path()).isEqualTo("data.items[0]");
        assertThat(data.requiredArray("items").size()).isEqualTo(1);
    }

    @Test
    void typedValues_areReadFromTheirFields() {
        assertThat(data.text("name")).isEqualTo("Тестов");
        assertThat(data.uuid("id")).isEqualTo(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"));
        assertThat(data.date("date")).isEqualTo(LocalDate.parse("2026-04-10"));
        assertThat(data.dateOrNull("date")).isEqualTo(LocalDate.parse("2026-04-10"));
        assertThat(data.decimal("amount")).isEqualByComparingTo(new BigDecimal("34000.00"));
        assertThat(data.bool("flag")).isTrue();
        assertThat(data.optionalText("name")).contains("Тестов");
        assertThat(data.textOrNull("name")).isEqualTo("Тестов");
    }

    @Test
    void optionalFields_areEmptyWhenMissingOrNotText() {
        assertThat(data.optionalText("missing")).isEmpty();
        assertThat(data.optionalText("number")).isEmpty();
        assertThat(data.textOrNull("missing")).isNull();
        assertThat(data.dateOrNull("missing")).isNull();
    }

    @Test
    void missingRequiredFields_areViolationsNamingTheFullPath() {
        assertThatThrownBy(() -> data.text("missing")).isInstanceOf(ContractViolationException.class)
                .hasMessage("нет обязательного поля data.missing");
        assertThatThrownBy(() -> data.text("number")).hasMessage("нет обязательного поля data.number");
        assertThatThrownBy(() -> data.decimal("name")).hasMessage("нет обязательного поля data.amount".replace("amount", "name"));
        assertThatThrownBy(() -> data.bool("name")).hasMessage("нет обязательного поля data.name");
        assertThatThrownBy(() -> data.requiredObject("name")).hasMessage("нет обязательного поля data.name");
        assertThatThrownBy(() -> data.requiredArray("nested")).hasMessage("нет обязательного поля data.nested");
        assertThatThrownBy(() -> root.requiredObject("missing")).hasMessage("нет обязательного поля missing");
    }

    @Test
    void malformedValues_areViolationsWithoutTheValue() {
        assertThatThrownBy(() -> data.uuid("name")).isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.name");
        assertThatThrownBy(() -> data.date("name")).hasMessage("недопустимое значение поля data.name");
        assertThatThrownBy(() -> data.dateOrNull("name")).hasMessage("недопустимое значение поля data.name");
    }
}
