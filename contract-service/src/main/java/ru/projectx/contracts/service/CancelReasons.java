package ru.projectx.contracts.service;

import java.util.Map;
import java.util.Optional;
import ru.projectx.contracts.entity.CancelReason;

public final class CancelReasons {

    private static final Map<String, CancelReason> BY_CODE = Map.of(
            "CKZ_REJECTED", CancelReason.REQUEST_CANCELLED,
            "HR_REJECTED", CancelReason.REQUEST_CANCELLED,
            "INSURED_REFUSED", CancelReason.INSURED_REFUSED,
            "EXPIRED", CancelReason.EXPIRED,
            "OTHER", CancelReason.OTHER);

    private CancelReasons() {
    }

    public static Optional<CancelReason> of(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}
