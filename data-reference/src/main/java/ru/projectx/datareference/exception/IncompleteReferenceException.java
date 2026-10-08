package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.UUID;

/**
 * Данных 1С не хватает для ответа: например, у ЛПУ (лечебно-профилактического учреждения) нет действующего
 * договора с ценами. Подставить что-то вместо ответа нельзя, поэтому сервис отвечает 422. Это ошибка данных в 1С,
 * а не вызывающего: отказ пишется в журнал с идентификатором ЛПУ, чтобы данные исправили.
 */
public final class IncompleteReferenceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String NO_ACTIVE_PRICE_CONTRACT = "NO_ACTIVE_PRICE_CONTRACT";

    private final String code;
    private final UUID lpuId;

    private IncompleteReferenceException(String code, String message, UUID lpuId) {
        super(message);
        this.code = code;
        this.lpuId = lpuId;
    }

    public static IncompleteReferenceException noActivePriceContract(UUID lpuId) {
        return new IncompleteReferenceException(NO_ACTIVE_PRICE_CONTRACT,
                "у ЛПУ " + lpuId + " нет действующего договора, по которому брать цены", lpuId);
    }

    /**
     * Договор ЛПУ, по которому сейчас берутся цены, у учреждения есть, но услуга относится к другому его договору.
     * Считать по ценам договора, который больше не прайсовый, нельзя, поэтому код тот же, что и при отсутствии
     * договора.
     */
    public static IncompleteReferenceException serviceOutsidePriceContract(UUID lpuId) {
        return new IncompleteReferenceException(NO_ACTIVE_PRICE_CONTRACT,
                "услуга не относится к прайсовому договору ЛПУ " + lpuId, lpuId);
    }

    public String code() {
        return code;
    }

    /** ЛПУ, данные которого надо исправить в 1С. */
    public UUID lpuId() {
        return lpuId;
    }
}
