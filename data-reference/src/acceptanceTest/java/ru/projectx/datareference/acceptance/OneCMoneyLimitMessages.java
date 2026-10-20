package ru.projectx.datareference.acceptance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import tools.jackson.databind.node.ObjectNode;

final class OneCMoneyLimitMessages {

    static final BigDecimal ONE_HUNDREDTH = new BigDecimal("0.01");

    private static final Set<String> PERCENT_FIELDS = Set.of("copayment", "percentCopayment");
    private static final Set<String> PROGRAM_FIELDS = Set.of("percentCopayment", "numberLayer");
    private static final Map<String, BigDecimal> AMOUNT_RECORDS = Map.of(
            "экспонента с огромным порядком", new BigDecimal("1e500"),
            "экспонента сразу за пределом", new BigDecimal("1E+13"),
            "отрицательное на одну сотую за пределом", new BigDecimal("-10000000000000.00"),
            "лишняя цифра слева и третий знак справа", new BigDecimal("10000000000000.001"),
            "экспонента сразу за прежним пределом длины", new BigDecimal("1e1000"),
            "отрицательное с экспонентой сразу за прежним пределом длины", new BigDecimal("-1e1000"),
            "экспонента с наибольшим порядком, который примет база", new BigDecimal("1e131071"),
            "отрицательный порядок сразу за прежним пределом длины", new BigDecimal("1e-1000"));
    private static final Map<String, BigDecimal> PERCENT_RECORDS = Map.of(
            "на одну сотую за пределом", new BigDecimal("1000.00"),
            "экспонента с огромным порядком", new BigDecimal("1e500"),
            "экспонента сразу за пределом", new BigDecimal("1E+3"),
            "отрицательное на одну сотую за пределом", new BigDecimal("-1000.00"),
            "лишняя цифра слева и третий знак справа", new BigDecimal("1000.001"),
            "экспонента сразу за прежним пределом длины", new BigDecimal("1e1000"),
            "экспонента с наибольшим порядком, который примет база", new BigDecimal("1e131071"),
            "отрицательный порядок сразу за прежним пределом длины", new BigDecimal("1e-1000"));

    private static final Set<String> INTEGER_FIELDS = Set.of(
            "layerNumber", "numberLayer", "rowNumber", "hierarchyLevels", "hierarchyLevel", "countMessages", "countRows");
    private static final Map<String, BigDecimal> INTEGER_RECORDS = Map.of(
            "экспонента за пределом целого", new BigDecimal("1e10"),
            "отрицательное с экспонентой за пределом целого", new BigDecimal("-1e20"),
            "дробное за пределом целого", new BigDecimal("10000000000.5"),
            "экспонента сразу за прежним пределом длины", new BigDecimal("1e1000"),
            "дробное в пределах целого", new BigDecimal("2.5"),
            "отрицательное дробное", new BigDecimal("-2.5"),
            "целое с нулевой дробной частью", new BigDecimal("2.0"));

    private OneCMoneyLimitMessages() {
    }

    static BigDecimal recorded(String field, String record) {
        return Optional.ofNullable(recordsOf(field).get(record))
                .orElseThrow(() -> new IllegalArgumentException("шаг не знает записи числа: " + record));
    }

    private static Map<String, BigDecimal> recordsOf(String field) {
        if (INTEGER_FIELDS.contains(field)) {
            return INTEGER_RECORDS;
        }
        return PERCENT_FIELDS.contains(field) ? PERCENT_RECORDS : AMOUNT_RECORDS;
    }

    static OneCMessage program(OneCMessage program, String field, String record) {
        if (!PROGRAM_FIELDS.contains(field)) {
            throw new IllegalArgumentException("шаг не знает поля строки услуги программы: " + field);
        }
        ObjectNode data = OneCProgramTermsMessages.renamed(program);
        OneCProgramTermsMessages.firstRow(data).put(field, recorded(field, record));
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.FAILED);
    }
}
