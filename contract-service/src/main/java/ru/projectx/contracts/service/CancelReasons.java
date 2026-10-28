package ru.projectx.contracts.service;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import ru.projectx.contracts.entity.issuance.CancelReason;

public final class CancelReasons {

    private static final Map<String, CancelReason> BY_CODE = Map.of(
            "CKZ_REJECTED", CancelReason.REQUEST_CANCELLED,
            "HR_REJECTED", CancelReason.REQUEST_CANCELLED,
            "INSURED_REFUSED", CancelReason.INSURED_REFUSED,
            "EXPIRED", CancelReason.EXPIRED,
            "OTHER", CancelReason.OTHER);
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,49}");

    private CancelReasons() {
    }

    public static Optional<CancelReason> of(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    public static Optional<String> wellFormed(String code) {
        return Optional.of(code).filter(candidate -> CODE.matcher(candidate).matches());
    }
}
