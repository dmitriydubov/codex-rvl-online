package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.List;
import java.util.UUID;
import ru.projectx.datareference.dto.ErrorResponse;

/**
 * Данные 1С допускают больше одного ответа там, где ответ должен быть один:
 * два полиса с одним номером в договоре, два кандидата на прайсовый договор.
 * Выбирать наугад нельзя — лучше заметный отказ, чем тихо неверная запись, — поэтому
 * {@code 409} с перечнем конфликтующих записей в {@code details}: по ним разбор
 * идёт на стороне 1С.
 */
public class AmbiguousReferenceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String AMBIGUOUS_POLICY = "AMBIGUOUS_POLICY";
    /** Открыт для каталога R-01: там та же неоднозначность — причина пропуска, а не отказ. */
    public static final String AMBIGUOUS_PRICE_CONTRACT = "AMBIGUOUS_PRICE_CONTRACT";

    private final String code;
    private final transient List<ErrorResponse.Detail> details;

    private AmbiguousReferenceException(String code, String message, List<ErrorResponse.Detail> details) {
        super(message);
        this.code = code;
        this.details = List.copyOf(details);
    }

    public static AmbiguousReferenceException policy(List<UUID> policyIds) {
        return new AmbiguousReferenceException(AMBIGUOUS_POLICY,
                "по номеру полиса в договоре найдено больше одной записи",
                policyIds.stream().<ErrorResponse.Detail>map(ErrorResponse.PolicyCandidate::new).toList());
    }

    /** Базовая логика B: у ЛПУ больше одного действующего кандидата на прайсовый договор. */
    public static AmbiguousReferenceException priceContract(UUID lpuId, List<UUID> lpuContractIds) {
        return new AmbiguousReferenceException(AMBIGUOUS_PRICE_CONTRACT,
                "у ЛПУ " + lpuId + " больше одного действующего кандидата на прайсовый договор",
                lpuContractIds.stream().<ErrorResponse.Detail>map(ErrorResponse.LpuContractCandidate::new).toList());
    }

    public String code() {
        return code;
    }

    public List<ErrorResponse.Detail> details() {
        return details;
    }
}
