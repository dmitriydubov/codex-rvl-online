package ru.projectx.datareference.acceptance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import ru.projectx.datareference.entity.InboundStatus;
import tools.jackson.databind.node.ObjectNode;

/**
 * Сообщения программы РВЛ, у строки услуги которой испорчены или отсутствуют обязательные условия: номер слоя,
 * прямое прикрепление и процент сооплаты (ТЗ-6), — или изменён тип покрытия. Каждое строится из программы, присланной сценарием раньше: тот же
 * GUID и та же строка услуги, меняется только то, что называет шаг.
 *
 * <p>Словари шагов закрытые: незнакомое имя поля или слово роняет шаг, иначе испорченное значение из Примеров молча
 * дало бы другое сообщение и сценарий прошёл бы на нём.
 */
final class OneCProgramTermsMessages {

    static final String NEW_NAME = "Стенд: восстановительное лечение, редакция 2";

    private static final String SERVICE = "service";
    private static final String UNKNOWN_SANATORIUM = "4c1f0e6a-3b5d-4f8e-9a7c-2d6b8e0f1a3c";
    private static final Set<String> REQUIRED_TERMS = Set.of("numberLayer", "directAttachment", "percentCopayment");
    /** Значение «текстом» — то же, что у программы сценария, но строкой JSON. */
    private static final Map<String, String> AS_TEXT = Map.of(
            "numberLayer", "1", "directAttachment", "true", "percentCopayment", "10.00");
    private static final Map<String, BiConsumer<ObjectNode, String>> SENT_AS = Map.of(
            "нет", ObjectNode::remove,
            "null", ObjectNode::putNull,
            "текст", (row, field) -> row.put(field, AS_TEXT.get(field)),
            "дробное число", (row, field) -> row.put(field, new BigDecimal("1.5")),
            "логическое значение", (row, field) -> row.put(field, true),
            "целое число", (row, field) -> row.put(field, 1));
    private static final Map<String, Consumer<ObjectNode>> DROPPED_ROW = Map.of(
            "по санаторию вне справочника", row -> row.put("idLPU", UNKNOWN_SANATORIUM),
            "без типа покрытия", row -> row.put("r", false).put("d", false),
            "с рисковой и депозитной частью сразу", row -> row.put("r", true).put("d", true));
    private static final Map<String, Boolean> FLAGS = Map.of("true", true, "false", false);

    private OneCProgramTermsMessages() {
    }

    /** Та же программа под новым наименованием; поле единственной строки услуги прислано так, как сказано словом. */
    static OneCMessage withTerm(OneCMessage program, String field, String sentAs) {
        ObjectNode data = renamed(program);
        lookup(SENT_AS, sentAs).accept(firstRow(data), requiredTerm(field));
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.FAILED);
    }

    /** Та же программа под новым наименованием и с ещё одной строкой, которую разбор не сохраняет, без поля. */
    static OneCMessage withDroppedRow(OneCMessage program, String droppedRow, String field) {
        ObjectNode data = renamed(program);
        ObjectNode extra = firstRow(data).deepCopy();
        extra.remove(requiredTerm(field));
        markDropped(extra, droppedRow);
        addRow(data, extra);
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.PROCESSED);
    }

    /** Та же программа под новым наименованием и с ещё одной строкой, которую разбор не сохраняет. */
    static OneCMessage withDroppedRow(OneCMessage program, String droppedRow) {
        ObjectNode data = renamed(program);
        ObjectNode extra = firstRow(data).deepCopy();
        markDropped(extra, droppedRow);
        addRow(data, extra);
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.PROCESSED);
    }

    /** Та же программа под новым наименованием; у строки услуги флаги рисковой и депозитной части — как сказано. */
    static OneCMessage withCoverage(OneCMessage program, String risk, String deposit) {
        ObjectNode data = renamed(program);
        firstRow(data).put("r", lookup(FLAGS, risk)).put("d", lookup(FLAGS, deposit));
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.PROCESSED);
    }

    /** Та же программа без признака РВЛ; в её строке услуги поля нет. */
    static OneCMessage withoutRvl(OneCMessage program, String field) {
        ObjectNode data = program.data().deepCopy().put("rvl", false);
        firstRow(data).remove(requiredTerm(field));
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.PROCESSED);
    }

    static ObjectNode renamed(OneCMessage program) {
        return program.data().deepCopy().put("name", NEW_NAME);
    }

    static ObjectNode firstRow(ObjectNode data) {
        return (ObjectNode) data.withArray(SERVICE).get(0);
    }

    /** Делает строку такой, какую разбор не сохраняет: вид строки — из закрытого словаря шага. */
    static void markDropped(ObjectNode row, String droppedRow) {
        lookup(DROPPED_ROW, droppedRow).accept(row);
    }

    static void addRow(ObjectNode data, ObjectNode row) {
        data.withArray(SERVICE).add(row);
    }

    private static String requiredTerm(String field) {
        if (!REQUIRED_TERMS.contains(field)) {
            throw new IllegalArgumentException("шаг не знает обязательного поля строки услуги: " + field);
        }
        return field;
    }

    private static <T> T lookup(Map<String, T> vocabulary, String word) {
        return Optional.ofNullable(vocabulary.get(word))
                .orElseThrow(() -> new IllegalArgumentException("шаг не знает слова: " + word));
    }
}
