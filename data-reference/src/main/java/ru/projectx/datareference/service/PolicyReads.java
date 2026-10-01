package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.PolicyResponse;

/** Полис целиком (R-05) по нашему {@code id}, по GUID 1С или по номеру в договоре. */
public interface PolicyReads {

    PolicyResponse byId(UUID id);

    PolicyResponse byExternalId(UUID externalId);

    /** Номер уникален в пределах договора, не глобально; два полиса с одним номером — {@code 409}. */
    PolicyResponse byNumber(String number, UUID contractId);
}
