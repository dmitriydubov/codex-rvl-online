package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Защищает чтение поля {@code data} из конверта, сохранённого в строке сырья: по одному правилу его читают разбор
 * сообщения, сборка передачи прайса и отказ передачи.
 *
 * <p>Стёртое тело — пустой результат: что с ним делать, решает вызывающий. Конверт без объекта {@code data} и тело,
 * которое не читается как JSON, — нарушение контракта, а текст отказа не цитирует тело.
 */
class StoredEnvelopeTest {

    private static final UUID MESSAGE_ID = UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000cc");

    private final StoredEnvelope envelope = new StoredEnvelope(JsonMapper.builder().build());

    @Test
    void dataOfEnvelope_isRead() {
        assertThat(envelope.dataOf(message("{\"data\":{\"code\":\"A00\"},\"metadata\":{}}")))
                .hasValueSatisfying(data -> assertThat(data.path("code").asString()).isEqualTo("A00"));
    }

    @Test
    void erasedPayload_isNoData() {
        assertThat(envelope.dataOf(message(null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"metadata\":{}}", "{\"data\":[1]}", "{\"data\":\"A00\"}", "[1]"})
    void envelopeWithoutDataObject_isContractViolation(String payload) {
        assertThatThrownBy(() -> envelope.dataOf(message(payload)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("в конверте сообщения нет объекта data");
    }

    @Test
    void bodyThatIsNotJson_isContractViolation_withoutQuotingTheBody() {
        assertThatThrownBy(() -> envelope.dataOf(message("{не json")))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("тело сообщения не читается как JSON")
                .cause()
                .hasMessageStartingWith("tools.jackson.")
                .hasMessageNotContaining("не json");
    }

    private static InboundMessage message(String payload) {
        return InboundMessage.receive(
                new InboundReceipt(MESSAGE_ID, OneCStream.PRICE_LISTS, "очередь", Instant.EPOCH), payload);
    }
}
