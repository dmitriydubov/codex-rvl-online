package ru.projectx.contracts.service.inbox.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.ContractViolationException;
import tools.jackson.databind.json.JsonMapper;

class JsonFieldsTest {

    private static final String BODY = """
            {"data": {"name": "Тестов", "items": [{"n": 1}, {"n": 2}], "nested": {"inner": {}}, "nothing": null}}
            """;

    private final JsonFields root = new JsonFields(JsonMapper.builder().build().readTree(BODY), "");
    private final JsonFields data = root.requiredObject("data");

    @Test
    void paths_startAtRootWithoutLeadingDot() {
        assertThat(root.field("data").path()).isEqualTo("data");
        assertThat(data.path()).isEqualTo("data");
        assertThat(data.field("name").path()).isEqualTo("data.name");
        assertThat(data.requiredObject("nested").requiredObject("inner").path()).isEqualTo("data.nested.inner");
        assertThat(data.requiredArray("items").item(1).path()).isEqualTo("data.items[1]");
    }

    @Test
    void fieldsAndItems_holdTheValuesUnderTheirNames() {
        assertThat(data.field("name").text()).isEqualTo("Тестов");
        assertThat(data.requiredArray("items").size()).isEqualTo(2);
        assertThat(data.requiredArray("items").item(1).field("n").integer()).isEqualTo(2);
    }

    @Test
    void missingOrNullStructures_areViolationsNamingTheFullPath() {
        assertThatThrownBy(() -> data.requiredObject("missing")).isInstanceOf(ContractViolationException.class)
                .hasMessage("нет обязательного поля data.missing");
        assertThatThrownBy(() -> data.requiredArray("missing")).hasMessage("нет обязательного поля data.missing");
        assertThatThrownBy(() -> data.requiredObject("nothing")).hasMessage("нет обязательного поля data.nothing");
        assertThatThrownBy(() -> data.requiredArray("nothing")).hasMessage("нет обязательного поля data.nothing");
        assertThatThrownBy(() -> root.requiredObject("missing")).hasMessage("нет обязательного поля missing");
    }

    @Test
    void presentStructuresOfWrongType_areInvalidValuesNamingTheFullPath() {
        assertThatThrownBy(() -> data.requiredObject("name")).isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.name");
        assertThatThrownBy(() -> data.requiredObject("items")).hasMessage("недопустимое значение поля data.items");
        assertThatThrownBy(() -> data.requiredArray("nested")).hasMessage("недопустимое значение поля data.nested");
        assertThatThrownBy(() -> data.requiredArray("name")).hasMessage("недопустимое значение поля data.name");
    }

    @Test
    void arrayPresence_isFalseForAbsentOrNullAndAViolationForAnotherType() {
        assertThat(data.hasArray("items")).isTrue();
        assertThat(data.hasArray("missing")).isFalse();
        assertThat(data.hasArray("nothing")).isFalse();
        assertThatThrownBy(() -> data.hasArray("name")).isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.name");
        assertThatThrownBy(() -> data.hasArray("nested")).hasMessage("недопустимое значение поля data.nested");
    }
}
