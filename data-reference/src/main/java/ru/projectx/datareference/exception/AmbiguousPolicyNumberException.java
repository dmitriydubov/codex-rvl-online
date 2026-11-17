package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.List;

public final class AmbiguousPolicyNumberException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String AMBIGUOUS_POLICY = "AMBIGUOUS_POLICY";

    private final transient List<ContractPolicy> candidates;

    private AmbiguousPolicyNumberException(List<ContractPolicy> candidates) {
        super("по номеру полиса найдено больше одной записи");
        this.candidates = List.copyOf(candidates);
    }

    public static AmbiguousPolicyNumberException of(List<ContractPolicy> candidates) {
        return new AmbiguousPolicyNumberException(candidates);
    }

    public String code() {
        return AMBIGUOUS_POLICY;
    }

    public List<ContractPolicy> candidates() {
        return candidates;
    }
}
