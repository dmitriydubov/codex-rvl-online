package ru.projectx.contracts.service.integration.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.exception.InboxEnvelopeException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EnvelopeBodiesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final InboxReceipt RECEIPT = new InboxReceipt(UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11"),
            UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"), "rvl.contract-task.upserted", "ContractTaskUpserted",
            1);

    @Test
    void jsonBody_isReadAsATree() {
        JsonNode body = EnvelopeBodies.read(JSON, "{\"meta\":{\"event_type\":\"ContractTaskUpserted\"}}"
                .getBytes(StandardCharsets.UTF_8));

        assertThat(body.path("meta").path("event_type").stringValue()).isEqualTo("ContractTaskUpserted");
    }

    @Test
    void bodyThatIsNotJson_isUnreadableWithoutItsTextInTheReason() {
        assertThatThrownBy(() -> EnvelopeBodies.read(JSON, "<не JSON> Тестова".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(InboxEnvelopeException.class,
                        lost -> assertThat(lost.lossReason()).isEqualTo("unreadable_body"))
                .hasMessage("тело не читается как JSON")
                .hasCauseInstanceOf(JacksonException.class);
    }

    @Test
    void bodyTheDatabaseCanStore_isAStorableEnvelopeOfItsReceipt() {
        JsonNode body = JSON.readTree("{\"data\":{\"comment\":\"Стенд: комментарий\"}}");

        assertThat(EnvelopeBodies.envelope(body, RECEIPT)).isEqualTo(new TaskEnvelope.Storable(RECEIPT));
    }

    @Test
    void bodyWithTextTheDatabaseCannotStore_isAnUnstorableEnvelopeOfItsReceipt() {
        JsonNode body = JSON.readTree("{\"data\":{\"comment\":\"Стенд\\u0000\"}}");

        assertThat(EnvelopeBodies.envelope(body, RECEIPT)).isEqualTo(new TaskEnvelope.Unstorable(RECEIPT));
    }
}
