package ru.projectx.contracts.exception;

import java.util.Arrays;
import java.util.Optional;

public enum ReferenceKind {

    PROGRAM("PROGRAM_NOT_FOUND"),
    INSURANCE_CONTRACT("INSURANCE_CONTRACT_NOT_FOUND"),
    POLICY("POLICY_NOT_FOUND");

    private final String notFoundCode;

    ReferenceKind(String notFoundCode) {
        this.notFoundCode = notFoundCode;
    }

    public String notFoundCode() {
        return notFoundCode;
    }

    public static Optional<ReferenceKind> byNotFoundCode(String code) {
        return Arrays.stream(values()).filter(kind -> kind.notFoundCode.equals(code)).findFirst();
    }
}
