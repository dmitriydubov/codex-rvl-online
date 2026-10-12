package ru.projectx.contracts.exception;

import java.util.Arrays;
import java.util.Optional;

/**
 * Справочник сервиса справочных данных, который читает сервис Договоров, и код, которым тот отвечает на отсутствие
 * записи. По коду из ответа определяется, какой справочник не нашёл запись.
 */
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
