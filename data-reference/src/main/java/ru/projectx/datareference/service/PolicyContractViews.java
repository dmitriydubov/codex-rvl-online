package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.PolicyContractView;
import ru.projectx.datareference.exception.ReferenceNotFoundException;

/**
 * Чтение полиса для сервиса Договоров без персональных данных: номер, {@code external_id}, период и признак архива.
 * Персональные данные Договоры получают от сервиса Заявок; полис с ними отдаётся только Заявкам.
 */
public interface PolicyContractViews {

    /**
     * Архивный полис отдаётся с признаком, а не как «не найден».
     *
     * @throws ReferenceNotFoundException полиса с таким {@code id} нет
     */
    PolicyContractView byId(UUID id);

    /**
     * По GUID 1С:УСК сервис Договоров находит наш {@code id} выпущенного полиса.
     *
     * @throws ReferenceNotFoundException полиса с таким {@code external_id} нет
     */
    PolicyContractView byExternalId(UUID externalId);
}
