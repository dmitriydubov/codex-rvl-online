package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.PolicyResponse;

/** Чтение полиса целиком для сервиса Заявок по нашему {@code id}, по GUID 1С или по номеру в договоре. */
public interface PolicyReads {

    PolicyResponse byId(UUID id);

    PolicyResponse byExternalId(UUID externalId);

    /** Номер уникален только в договоре, поэтому два полиса с одним номером в нём дают 409. */
    PolicyResponse byNumber(String number, UUID contractExternalId);
}
