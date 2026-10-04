package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.UUID;

/**
 * Данных 1С не хватает, чтобы ответить. Например, у ЛПУ (лечебно-профилактического учреждения) нет ни
 * одного действующего договора, по которому брать цены. Запрос верен и записи есть, но ответ из них не
 * собрать, а подставить что-то вместо него нельзя. Поэтому сервис отвечает 422.
 *
 * <p>Как и при 409, это ошибка данных в 1С, а не вызывающего. Отказ пишется в журнал на уровне WARN,
 * и по таким записям собирают очередь на исправление.
 */
public final class IncompleteReferenceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String NO_ACTIVE_PRICE_CONTRACT = "NO_ACTIVE_PRICE_CONTRACT";

    private final String code;

    private IncompleteReferenceException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Среди действующих договоров ЛПУ нет ни одного, по которому брать цены. */
    public static IncompleteReferenceException noActivePriceContract(UUID lpuId) {
        return new IncompleteReferenceException(NO_ACTIVE_PRICE_CONTRACT,
                "у ЛПУ " + lpuId + " нет действующего договора, по которому брать цены");
    }

    public String code() {
        return code;
    }
}
