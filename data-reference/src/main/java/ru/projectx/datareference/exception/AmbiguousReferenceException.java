package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.List;
import java.util.UUID;

public final class AmbiguousReferenceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String AMBIGUOUS_POLICY = "AMBIGUOUS_POLICY";
    public static final String AMBIGUOUS_PRICE_CONTRACT = "AMBIGUOUS_PRICE_CONTRACT";

    public enum CandidateKind {
        POLICY,
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

    public List<UUID> candidateIds() {
        return candidateIds;
    }
}
