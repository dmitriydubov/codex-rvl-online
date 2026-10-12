package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.projectx.contracts.entity.InboxReceipt;
import ru.projectx.contracts.exception.InboxEnvelopeException;
import ru.projectx.contracts.support.TaskBodies;
import tools.jackson.databind.json.JsonMapper;

class TaskEnvelopeParserTest {

    private final TaskEnvelopeParser parser = new TaskEnvelopeParser(JsonMapper.builder().build());

    @Test
    void completeEnvelope_yieldsReceiptWithIdentifiersTopicTypeAndVersion() {
        InboxReceipt receipt = parser.parse(TaskBodies.upserted().toBytes());

        assertThat(receipt).isEqualTo(new InboxReceipt(UUID.fromString(TaskBodies.MESSAGE_ID),
                UUID.fromString(TaskBodies.REQUEST_ID), "rvl.contract-task.upserted", "ContractTaskUpserted", 1));
    }

    @ParameterizedTest
    @CsvSource({"message_id", "correlation_id", "topic", "event_type", "entity_version"})
    void missingEnvelopeField_isUnusableEnvelopeNamingTheField(String field) {
        byte[] body = TaskBodies.upserted().withoutMetaField(field).toBytes();

        assertThatThrownBy(() -> parser.parse(body))
                .isInstanceOf(InboxEnvelopeException.class)
                .hasMessage("в конверте нет пригодного " + field);
    }

    @ParameterizedTest
    @CsvSource({"message_id", "correlation_id", "entity_version"})
    void malformedEnvelopeField_isUnusableEnvelopeNamingTheField(String field) {
        byte[] body = TaskBodies.upserted().withMetaField(field, "1-2-3-4-5").toBytes();

        assertThatThrownBy(() -> parser.parse(body))
                .isInstanceOf(InboxEnvelopeException.class)
                .hasMessage("в конверте нет пригодного " + field);
    }

    @Test
    void topicOrEventTypeBeyondTheJournalColumn_isUnusableEnvelope() {
        byte[] longTopic = TaskBodies.upserted().withMetaField("topic", "t".repeat(101)).toBytes();
        byte[] longType = TaskBodies.upserted().withMetaField("event_type", "e".repeat(51)).toBytes();
        byte[] blankType = TaskBodies.upserted().withMetaField("event_type", " ").toBytes();

        assertThatThrownBy(() -> parser.parse(longTopic)).hasMessage("в конверте нет пригодного topic");
        assertThatThrownBy(() -> parser.parse(longType)).hasMessage("в конверте нет пригодного event_type");
        assertThatThrownBy(() -> parser.parse(blankType)).hasMessage("в конверте нет пригодного event_type");
        assertThat(parser.parse(TaskBodies.upserted().withMetaField("topic", "t".repeat(100)).toBytes()).topic())
                .hasSize(100);
    }

    @Test
    void bodyWithoutMeta_isUnusableEnvelope() {
        assertThatThrownBy(() -> parser.parse("{\"data\":{}}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InboxEnvelopeException.class)
                .hasMessage("в конверте нет пригодного message_id");
    }

    @Test
    void unreadableBody_isUnusableEnvelopeWithoutItsContent() {
        assertThatThrownBy(() -> parser.parse("{Тестов".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InboxEnvelopeException.class)
                .hasMessage("тело не читается как JSON");
    }

    @Test
    void versionBeyondInt_isUnusableEnvelope() {
        byte[] body = TaskBodies.upserted().toJson().replace("\"entity_version\":1", "\"entity_version\":99999999999")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(body)).hasMessage("в конверте нет пригодного entity_version");
    }
}
