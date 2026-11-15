package ru.projectx.datareference.controller;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.controller.error.RequestErrorAdvice;
import ru.projectx.datareference.exception.InvalidRequestException;

record PolicyNumberKey(String number, UUID contractExternalId) {

    static final String EXTERNAL_ID = "external_id";
    static final String NUMBER = "number";
    static final String CONTRACT_EXTERNAL_ID = "contract_external_id";
    static final String NO_KEY = "нужен external_id либо number вместе с contract_external_id";

    static PolicyNumberKey of(@Nullable String number, @Nullable UUID contractExternalId) {
        if (number == null && contractExternalId == null) {
            throw new InvalidRequestException(EXTERNAL_ID, NO_KEY);
        }
        return new PolicyNumberKey(present(number), present(contractExternalId));
    }

    private static String present(@Nullable String number) {
        if (number == null || number.isBlank()) {
            throw new InvalidRequestException(NUMBER, RequestErrorAdvice.REQUIRED_PARAMETER);
        }
        return number;
    }

    private static UUID present(@Nullable UUID contractExternalId) {
        if (contractExternalId == null) {
            throw new InvalidRequestException(CONTRACT_EXTERNAL_ID, RequestErrorAdvice.REQUIRED_PARAMETER);
        }
        return contractExternalId;
    }
}
