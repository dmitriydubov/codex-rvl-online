package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.PolicyResponse;

/** Полис целиком для сервиса Заявок: по нашему {@code id}, по GUID 1С или по номеру в договоре страхования. */
public interface PolicyReads {

    PolicyResponse byId(UUID id);

    PolicyResponse byExternalId(UUID externalId);

    /**
     * Договор задаётся GUID 1С. Номер уникален только в пределах договора, поэтому два полиса с одним номером
     * в одном договоре — ответ {@code 409}.
     */
    PolicyResponse byNumber(String number, UUID contractExternalId);
}
