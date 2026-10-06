package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    /** Исходы так, как их называет спецификация, и статусы строки сырья, которые за ними стоят. */
    private static final Map<String, InboundStatus> OUTCOMES = Map.of(
            "применено", InboundStatus.PROCESSED,
            "ждёт родителя", InboundStatus.AWAITING_PARENT,
            "пропущено", InboundStatus.SKIPPED,
            "отложено до повтора", InboundStatus.RETRY,
            "отклонено без повторов", InboundStatus.FAILED,
            "ждёт разбора", InboundStatus.NEW,
            "ждёт сборки передачи", InboundStatus.BUFFERED);

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

    /** То же для сообщения с заданным сценарием {@code message-id}, например соседней части передачи прайса. */
    @Тогда("сообщение 1С:УСК {string} отклонено без повторов с причиной {string}")
    public void messageRejected(String messageId, String reason) {
        InboundRow row = inbox.row(UUID.fromString(messageId));

        assertThat(row.status()).as("статус сообщения %s", messageId).isEqualTo(InboundStatus.FAILED.name());
        assertThat(row.lastError()).as("причина отказа сообщения %s", messageId).isEqualTo(reason);
    }

    /**
     * «Отложено до повтора» значит статус {@code RETRY}: отказ временный, и разбор повторят по лестнице.
     * Номер попытки — число отложенных попыток в строке; причина сравнивается дословно, поэтому в ней ровно тип
     * и SQLSTATE и нет текста ошибки драйвера.
     */
    @Тогда("последнее сообщение 1С:УСК отложено до повтора после попытки {int} с причиной {string}")
    public void lastMessagePostponed(int attempt, String reason) {
        assertPostponed(inbox.last(), "последнего", attempt, reason);
    }

    /** То же для сообщения, присланного первым: оно голова потока, и его исход проверяют после более поздних. */
    @Тогда("первое сообщение 1С:УСК отложено до повтора после попытки {int} с причиной {string}")
    public void firstMessagePostponed(int attempt, String reason) {
        assertPostponed(inbox.first(), "первого", attempt, reason);
    }

    /**
     * Исходы всех строк сырья сценария в порядке приёма, словами через запятую. Незнакомое слово роняет шаг: иначе
     * мутация значения в спецификации прошла бы незамеченной.
     */
    @Тогда("исходы сообщений 1С:УСК в порядке приёма — {string}")
    public void outcomesInOrder(String outcomes) {
        List<String> expected = Stream.of(outcomes.split(",")).map(String::strip).map(OneCOutcomeSteps::status).toList();

        assertThat(inbox.statuses()).as("статусы сырья в порядке приёма").containsExactlyElementsOf(expected);
    }

    /** То же для частей одной передачи прайса: часть узнаётся по идентификатору передачи. */
    @Тогда("исходы сообщений передачи прайса {string} в порядке приёма — {string}")
    public void transferOutcomesInOrder(String cid, String outcomes) {
        List<String> expected = Stream.of(outcomes.split(",")).map(String::strip).map(OneCOutcomeSteps::status).toList();

        assertThat(inbox.transferStatuses(UUID.fromString(cid)))
                .as("статусы частей передачи %s в порядке приёма", cid)
                .containsExactlyElementsOf(expected);
    }

    private static String status(String outcome) {
        assertThat(OUTCOMES).as("исходы, которые знает шаг").containsKey(outcome);
        return OUTCOMES.get(outcome).name();
    }

    private static void assertPostponed(InboundRow row, String which, int attempt, String reason) {
        assertThat(row.status()).as("статус %s сообщения", which).isEqualTo(InboundStatus.RETRY.name());
        assertThat(row.attempt()).as("отложенных попыток %s сообщения", which).isEqualTo(attempt);
        assertThat(row.lastError()).as("причина отказа %s сообщения", which).isEqualTo(reason);
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
