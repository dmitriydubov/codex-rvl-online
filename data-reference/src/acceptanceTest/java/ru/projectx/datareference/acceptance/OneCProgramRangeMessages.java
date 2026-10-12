package ru.projectx.datareference.acceptance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import ru.projectx.datareference.entity.InboundStatus;
import tools.jackson.databind.node.ObjectNode;

final class OneCProgramRangeMessages {

    private static final Map<String, BiConsumer<ObjectNode, BigDecimal>> NUMERIC_TERMS = Map.of(
            "numberLayer", (row, value) -> row.put("numberLayer", value.toBigIntegerExact()),
            "percentCopayment", (row, value) -> row.put("percentCopayment", value));
    private static final Map<String, BigDecimal> STEPS_PAST_LIMIT = Map.of(
            "на единицу меньше", BigDecimal.ONE.negate(),
            "на единицу больше", BigDecimal.ONE,
            "на одну сотую меньше", new BigDecimal("-0.01"),
            "на одну сотую больше", new BigDecimal("0.01"),
            "на одну тысячную больше", new BigDecimal("0.001"));
    private static final Map<String, BigDecimal> PAST_LIMIT = Map.of(
            "numberLayer", BigDecimal.ONE.negate(),
            "percentCopayment", new BigDecimal("100.01"));

    private OneCProgramRangeMessages() {
    }

    static OneCMessage withTerm(OneCMessage program, String field, String number) {
        ObjectNode data = program.data().deepCopy();
        lookup(NUMERIC_TERMS, field).accept(OneCProgramTermsMessages.firstRow(data), new BigDecimal(number));
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.PROCESSED);
    }

    static OneCMessage withTermPastLimit(OneCMessage program, String field, String stepPastLimit, String limit) {
        ObjectNode data = OneCProgramTermsMessages.renamed(program);
        BigDecimal value = new BigDecimal(limit).add(lookup(STEPS_PAST_LIMIT, stepPastLimit));
        lookup(NUMERIC_TERMS, field).accept(OneCProgramTermsMessages.firstRow(data), value);
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.FAILED);
    }

    static OneCMessage withDroppedRowPastLimit(OneCMessage program, String droppedRow, String field) {
        ObjectNode data = OneCProgramTermsMessages.renamed(program);
        ObjectNode extra = OneCProgramTermsMessages.firstRow(data).deepCopy();
        pastLimit(extra, field);
        OneCProgramTermsMessages.markDropped(extra, droppedRow);
        OneCProgramTermsMessages.addRow(data, extra);
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.PROCESSED);
    }

    static OneCMessage withoutRvlPastLimit(OneCMessage program, String field) {
        ObjectNode data = program.data().deepCopy().put("rvl", false);
        pastLimit(OneCProgramTermsMessages.firstRow(data), field);
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.PROCESSED);
    }

    private static void pastLimit(ObjectNode row, String field) {
        lookup(NUMERIC_TERMS, field).accept(row, lookup(PAST_LIMIT, field));
    }

    private static <T> T lookup(Map<String, T> vocabulary, String word) {
        return Optional.ofNullable(vocabulary.get(word))
                .orElseThrow(() -> new IllegalArgumentException("шаг не знает слова: " + word));
    }
}
