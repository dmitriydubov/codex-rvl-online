package ru.projectx.contracts.service.inbox.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.exception.ContractViolationException;
import tools.jackson.databind.json.JsonMapper;

class FieldPresenceTest {

    private static final String BODY = """
            {"data": {"number": "LM-000012204", "count": 5, "documents": [], "nothing": null}}
            """;

    private static final MessageBodies BODIES = new MessageBodies(JsonMapper.builder().build());

    private final JsonFields data = BODIES.data(BODY);

    @Test
    void requiredText_isReadAndItsAbsenceIsAViolation() {
        assertThat(FieldPresence.REQUIRED.textOf(data, "number")).isEqualTo("LM-000012204");
        assertThatThrownBy(() -> FieldPresence.REQUIRED.textOf(data, "missing"))
                .isInstanceOf(ContractViolationException.class).hasMessage("нет обязательного поля data.missing");
        assertThatThrownBy(() -> FieldPresence.REQUIRED.textOf(data, "count"))
                .hasMessage("недопустимое значение поля data.count");
    }

    @Test
    void optionalText_isNullWhenAbsentOrNull() {
        assertThat(FieldPresence.OPTIONAL.textOf(data, "number")).isEqualTo("LM-000012204");
        assertThat(FieldPresence.OPTIONAL.textOf(data, "missing")).isNull();
        assertThat(FieldPresence.OPTIONAL.textOf(data, "nothing")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "false", "[]", "{}"})
    void optionalText_rejectsOtherJsonTypes(String value) {
        JsonFields fields = BODIES.data("{\"data\":{\"number\":" + value + "}}");

        assertThatThrownBy(() -> FieldPresence.OPTIONAL.textOf(fields, "number"))
                .isInstanceOf(ContractViolationException.class).hasMessage("недопустимое значение поля data.number");
    }

    @Test
    void requiredArray_isNeverReportedMissing() {
        assertThat(FieldPresence.REQUIRED.arrayMissingIn(data, "documents")).isFalse();
        assertThat(FieldPresence.REQUIRED.arrayMissingIn(data, "missing")).isFalse();
        assertThat(FieldPresence.REQUIRED.arrayMissingIn(data, "number")).isFalse();
    }

    @Test
    void optionalArray_isMissingWhenAbsentOrNullAndAViolationForAnotherType() {
        assertThat(FieldPresence.OPTIONAL.arrayMissingIn(data, "documents")).isFalse();
        assertThat(FieldPresence.OPTIONAL.arrayMissingIn(data, "missing")).isTrue();
        assertThat(FieldPresence.OPTIONAL.arrayMissingIn(data, "nothing")).isTrue();
        assertThatThrownBy(() -> FieldPresence.OPTIONAL.arrayMissingIn(data, "number"))
                .isInstanceOf(ContractViolationException.class).hasMessage("недопустимое значение поля data.number");
    }
}
