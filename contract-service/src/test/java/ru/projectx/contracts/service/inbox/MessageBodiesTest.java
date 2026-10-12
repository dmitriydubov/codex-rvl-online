package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.ContractViolationException;
import tools.jackson.databind.json.JsonMapper;

class MessageBodiesTest {

    private final MessageBodies bodies = new MessageBodies(JsonMapper.builder().build());

    @Test
    void data_isTheDataObjectOfTheBody() {
        JsonFields data = bodies.data("{\"meta\":{},\"data\":{\"individual_request_number\":\"IZ-1\"}}");

        assertThat(data.path()).isEqualTo("data");
        assertThat(data.text("individual_request_number")).isEqualTo("IZ-1");
    }

    @Test
    void bodyWithoutDataObject_isViolation() {
        assertThatThrownBy(() -> bodies.data("{\"data\":\"Тестов\"}"))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("нет обязательного поля data");
    }

    @Test
    void unreadableBody_isViolationWithoutItsContent() {
        assertThatThrownBy(() -> bodies.data("Тестов"))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("тело не читается как JSON");
    }
}
