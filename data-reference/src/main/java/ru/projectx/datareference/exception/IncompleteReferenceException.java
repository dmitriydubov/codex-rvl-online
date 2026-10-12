package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.UUID;

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

    public static IncompleteReferenceException serviceOutsidePriceContract(UUID lpuId) {
        return new IncompleteReferenceException(NO_ACTIVE_PRICE_CONTRACT,
                "услуга не относится к прайсовому договору ЛПУ " + lpuId, lpuId);
    }

    public String code() {
        return code;
    }

    public UUID lpuId() {
        return lpuId;
    }
}
