package ru.projectx.datareference.acceptance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ru.projectx.datareference.entity.InboundStatus;
import tools.jackson.databind.node.ObjectNode;

/**
 * Числа, которые не помещаются в колонку денег или процента, так, как их называет спецификация. Сами числа стоят здесь,
 * а не в «Примерах»: мутация цифры порядка оставила бы значение за пределом, и сценарий её бы не заметил. Словари
 * закрытые: незнакомое слово или поле роняет шаг.
 *
 * <p>Огромный порядок — {@code 1e500}, а не больше: сырьё сообщения хранится в {@code jsonb}, а его число — это
 * {@code numeric} PostgreSQL, у которого до запятой не больше 131072 цифр. Число с порядком больше база не примет в
 * сырьё, и сообщение уйдёт в карантин при приёме, до разбора.
 */
final class OneCMoneyLimitMessages {

    static final BigDecimal ONE_HUNDREDTH = new BigDecimal("0.01");

    /** Поля процента: у них колонка {@code numeric(5,2)}, у остальных денежных полей — {@code numeric(15,2)}. */
    private static final Set<String> PERCENT_FIELDS = Set.of("copayment", "percentCopayment");
    private static final Map<String, BigDecimal> AMOUNT_RECORDS = Map.of(
            "экспонента с огромным порядком", new BigDecimal("1e500"),
            "экспонента сразу за пределом", new BigDecimal("1E+13"),
            "отрицательное на одну сотую за пределом", new BigDecimal("-10000000000000.00"),
            "лишняя цифра слева и третий знак справа", new BigDecimal("10000000000000.001"));
    private static final Map<String, BigDecimal> PERCENT_RECORDS = Map.of(
            "на одну сотую за пределом", new BigDecimal("1000.00"),
            "экспонента с огромным порядком", new BigDecimal("1e500"),
            "экспонента сразу за пределом", new BigDecimal("1E+3"),
            "отрицательное на одну сотую за пределом", new BigDecimal("-1000.00"),
            "лишняя цифра слева и третий знак справа", new BigDecimal("1000.001"));

    private OneCMoneyLimitMessages() {
    }

    /** Число поля в записи, которую называет спецификация; у процента и у денег записи свои. */
    static BigDecimal recorded(String field, String record) {
        Map<String, BigDecimal> records = PERCENT_FIELDS.contains(field) ? PERCENT_RECORDS : AMOUNT_RECORDS;
        return Optional.ofNullable(records.get(record))
                .orElseThrow(() -> new IllegalArgumentException("шаг не знает записи числа: " + record));
    }

    /** Та же программа под новым наименованием; поле строки услуги — число в заданной записи. Разбор её отклоняет. */
    static OneCMessage program(OneCMessage program, String field, String record) {
        if (!PERCENT_FIELDS.contains(field)) {
            throw new IllegalArgumentException("шаг не знает поля строки услуги программы: " + field);
        }
        ObjectNode data = OneCProgramTermsMessages.renamed(program);
        OneCProgramTermsMessages.firstRow(data).put(field, recorded(field, record));
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.FAILED);
    }
}
