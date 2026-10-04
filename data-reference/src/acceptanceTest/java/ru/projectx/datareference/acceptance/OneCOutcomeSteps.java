package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.util.List;
import java.util.stream.Stream;
import ru.projectx.datareference.entity.InboundStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Шаги «Тогда» об исходе разбора последнего сообщения 1С:УСК.
 *
 * <p>Исход читается по строке сырья так, как её видит дежурный: статус, причина и тело сообщения.
 */
public class OneCOutcomeSteps {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String DATA = "data";

    private final OneCInbox inbox;

    public OneCOutcomeSteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    /**
     * «Без повторов» значит окончательный статус {@code FAILED}, а не {@code RETRY}: повтор нарушения
     * контракта дал бы то же самое. Причина сравнивается дословно, поэтому в ней ровно имя поля и нет
     * значения.
     */
    @Тогда("последнее сообщение 1С:УСК отклонено без повторов с причиной {string}")
    public void lastMessageRejected(String reason) {
        InboundRow last = inbox.last();

        assertThat(last.status()).as("статус последнего сообщения").isEqualTo(InboundStatus.FAILED.name());
        assertThat(last.lastError()).as("причина отказа последнего сообщения").isEqualTo(reason);
    }

    /**
     * Тело цело: это присланный конверт целиком, не стёртый и не переписанный. В {@code data} есть
     * строковое значение ровно в том виде, в каком его прислала 1С:УСК. Значит, сообщение можно
     * разобрать заново после починки, не запрашивая выгрузку повторно.
     *
     * <p>Конверты сравниваются деревьями JSON, потому что {@code jsonb} хранит ключи в своём порядке, а
     * равенство объектов Jackson от порядка не зависит.
     */
    @Тогда("сырьё последнего сообщения сохранено и содержит {string}")
    public void lastMessageRawKept(String value) {
        String payload = inbox.last().payload();

        assertThat(payload).as("тело последнего сообщения").isNotNull();
        JsonNode kept = JSON.readTree(payload);
        assertThat(kept).as("тело последнего сообщения — присланный конверт целиком")
                .isEqualTo(JSON.readTree(inbox.lastEnvelope()));
        assertThat(stringsOf(kept.path(DATA)))
                .as("строковые значения data последнего сообщения")
                .contains(value);
    }

    private static List<String> stringsOf(JsonNode node) {
        return withNested(node).filter(JsonNode::isString).map(JsonNode::stringValue).toList();
    }

    private static Stream<JsonNode> withNested(JsonNode node) {
        return Stream.concat(Stream.of(node), node.valueStream().flatMap(OneCOutcomeSteps::withNested));
    }
}
