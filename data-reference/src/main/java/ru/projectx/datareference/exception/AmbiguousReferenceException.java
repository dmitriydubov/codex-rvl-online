package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.List;
import java.util.UUID;

/**
 * Данные 1С допускают больше одного ответа там, где он должен быть один: два полиса с одним номером в договоре
 * или два договора ЛПУ (лечебно-профилактического учреждения) с ценами. Заметный отказ лучше тихо неверной
 * записи, поэтому сервис отвечает 409 и перечисляет конфликтующие записи, чтобы их разобрали в 1С.
 */
public final class AmbiguousReferenceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String AMBIGUOUS_POLICY = "AMBIGUOUS_POLICY";
    /** Код открыт для каталога ЛПУ: там та же неоднозначность становится причиной пропуска, а не отказом. */
    public static final String AMBIGUOUS_PRICE_CONTRACT = "AMBIGUOUS_PRICE_CONTRACT";

    public enum CandidateKind {
        /** Полисы с одним номером в одном договоре страхования. */
        POLICY,
        /** Договоры ЛПУ, каждый из которых может быть договором с ценами. */
        LPU_CONTRACT
    }

    private final String code;
    private final CandidateKind candidateKind;
    private final transient List<UUID> candidateIds;

    private AmbiguousReferenceException(String code, String message, CandidateKind kind, List<UUID> candidateIds) {
        super(message);
        this.code = code;
        this.candidateKind = kind;
        this.candidateIds = List.copyOf(candidateIds);
    }

    public static AmbiguousReferenceException policy(List<UUID> policyIds) {
        return new AmbiguousReferenceException(AMBIGUOUS_POLICY,
                "по номеру полиса в договоре найдено больше одной записи", CandidateKind.POLICY, policyIds);
    }

    /** У ЛПУ больше одного действующего договора, по которому можно брать цены. */
    public static AmbiguousReferenceException priceContract(UUID lpuId, List<UUID> lpuContractIds) {
        return new AmbiguousReferenceException(AMBIGUOUS_PRICE_CONTRACT,
                "у ЛПУ " + lpuId + " больше одного действующего кандидата на прайсовый договор",
                CandidateKind.LPU_CONTRACT, lpuContractIds);
    }

    public String code() {
        return code;
    }

    public CandidateKind candidateKind() {
        return candidateKind;
    }

    /** Идентификаторы конфликтующих записей в том порядке, в каком их нашёл сервис. */
    public List<UUID> candidateIds() {
        return candidateIds;
    }
}
