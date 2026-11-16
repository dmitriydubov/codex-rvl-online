package ru.projectx.contracts.service.inbox.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.ContractViolationException;
import tools.jackson.databind.json.JsonMapper;

class JsonFieldTest {

    private static final String BODY = """
            {"data": {"id": "61a7982c-a0b4-4bbf-9527-60b40b752803", "date": "2026-04-10", "amount": 34000.00,
                      "flag": true, "off": false, "name": "Тестов", "number": 5, "nothing": null,
                      "beyondInt": 99999999999}}
            """;

    private final JsonFields data = new JsonFields(JsonMapper.builder().build().readTree(BODY), "")
            .requiredObject("data");

    @Test
    void typedValues_areReadFromTheirFields() {
        assertThat(data.field("name").text()).isEqualTo("Тестов");
        assertThat(data.field("id").uuid()).isEqualTo(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"));
        assertThat(data.field("date").date()).isEqualTo(LocalDate.parse("2026-04-10"));
        assertThat(data.field("amount").decimal()).isEqualByComparingTo(new BigDecimal("34000.00"));
        assertThat(data.field("number").decimal()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(data.field("flag").bool()).isTrue();
        assertThat(data.field("off").bool()).isFalse();
    }

    @Test
    void missingOrNullFields_areViolationsNamingTheFullPath() {
        assertThatThrownBy(() -> data.field("missing").text()).isInstanceOf(ContractViolationException.class)
                .hasMessage("нет обязательного поля data.missing");
        assertThatThrownBy(() -> data.field("missing").uuid()).hasMessage("нет обязательного поля data.missing");
        assertThatThrownBy(() -> data.field("missing").date()).hasMessage("нет обязательного поля data.missing");
        assertThatThrownBy(() -> data.field("missing").decimal()).hasMessage("нет обязательного поля data.missing");
        assertThatThrownBy(() -> data.field("missing").bool()).hasMessage("нет обязательного поля data.missing");
        assertThatThrownBy(() -> data.field("nothing").text()).hasMessage("нет обязательного поля data.nothing");
        assertThatThrownBy(() -> data.field("nothing").bool()).hasMessage("нет обязательного поля data.nothing");
    }

    @Test
    void presentFieldsOfWrongType_areInvalidValuesNamingTheFullPath() {
        assertThatThrownBy(() -> data.field("number").text()).isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.number");
        assertThatThrownBy(() -> data.field("number").uuid()).hasMessage("недопустимое значение поля data.number");
        assertThatThrownBy(() -> data.field("number").date()).hasMessage("недопустимое значение поля data.number");
        assertThatThrownBy(() -> data.field("name").decimal()).hasMessage("недопустимое значение поля data.name");
        assertThatThrownBy(() -> data.field("name").bool()).hasMessage("недопустимое значение поля data.name");
    }

    @Test
    void integerField_isAWholeNumberThatFitsInt() {
        assertThat(data.field("number").integer()).isEqualTo(5);
        assertThatThrownBy(() -> data.field("amount").integer()).isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.amount");
        assertThatThrownBy(() -> data.field("beyondInt").integer())
                .hasMessage("недопустимое значение поля data.beyondInt");
        assertThatThrownBy(() -> data.field("name").integer()).hasMessage("недопустимое значение поля data.name");
        assertThatThrownBy(() -> data.field("missing").integer()).hasMessage("нет обязательного поля data.missing");
        assertThatThrownBy(() -> data.field("nothing").integer()).hasMessage("нет обязательного поля data.nothing");
    }

    @Test
    void malformedValues_areViolationsWithoutTheValue() {
        assertThatThrownBy(() -> data.field("name").uuid()).isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.name");
        assertThatThrownBy(() -> data.field("name").date()).hasMessage("недопустимое значение поля data.name")
                .hasCauseInstanceOf(DateTimeParseException.class);
    }
}
