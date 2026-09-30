package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.List;
import java.util.UUID;
import ru.projectx.datareference.dto.ErrorResponse;

/**
 * Данных 1С не хватает, чтобы ответить: у ЛПУ нет ни одного действующего договора,
 * в запрошенном периоде есть сутки без прайса. Запрос верен и записи есть, но ответа
 * из них не собрать, а подставить что-то вместо него нельзя — отсюда {@code 422}.
 * Как и {@code 409}, это проблема данных в 1С, а не вызывающего: пишется в журнал
 * на уровне WARN, по таким записям собирается очередь на исправление.
 */
public class IncompleteReferenceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String NO_ACTIVE_PRICE_CONTRACT = "NO_ACTIVE_PRICE_CONTRACT";

    private final String code;
    private final transient List<ErrorResponse.Detail> details;

    private IncompleteReferenceException(String code, String message, List<ErrorResponse.Detail> details) {
        super(message);
        this.code = code;
        this.details = List.copyOf(details);
    }

    /** Базовая логика B: среди действующих договоров ЛПУ нет ни одного, по которому брать цены. */
    public static IncompleteReferenceException noActivePriceContract(UUID lpuId) {
        return new IncompleteReferenceException(NO_ACTIVE_PRICE_CONTRACT,
                "у ЛПУ " + lpuId + " нет действующего договора, по которому брать цены", List.of());
    }

    public String code() {
        return code;
    }

    public List<ErrorResponse.Detail> details() {
        return details;
    }
}
