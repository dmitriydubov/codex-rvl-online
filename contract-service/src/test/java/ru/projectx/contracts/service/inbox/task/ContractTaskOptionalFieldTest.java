package ru.projectx.contracts.service.inbox.task;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.service.inbox.parse.MessageBodies;
import ru.projectx.contracts.support.TaskBodies;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ContractTaskOptionalFieldTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ContractTaskParser parser = new ContractTaskParser(new MessageBodies(JSON));

    @ParameterizedTest
    @ValueSource(strings = {"program_id", "personal_data.middle_name", "personal_data.relation_type",
            "personal_data.address", "personal_data.document.series", "personal_data.document.issued_by",
            "personal_data.document.issued_at", "personal_data.document.division_code"})
    void nonStringOptionalField_rejectsMessageWithExactIndexedPath(String path) {
        JsonNode task = JSON.readTree(TaskBodies.upserted().toJson());
        String pointer = "/data/participants/0/" + path.replace('.', '/');
        int leaf = pointer.lastIndexOf('/');
        ObjectNode parent = (ObjectNode) task.at(pointer.substring(0, leaf));
        parent.putObject(pointer.substring(leaf + 1)).put("test", "СТЕНД-ЗНАЧЕНИЕ");

        assertThatThrownBy(() -> parser.parse(JSON.writeValueAsString(task)))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.participants[0]." + path).hasNoCause();
    }
}
