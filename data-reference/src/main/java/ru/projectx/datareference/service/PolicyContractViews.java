package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.PolicyContractView;
import ru.projectx.datareference.exception.ReferenceNotFoundException;

/**
 * Полис для сервиса Договоров — без персональных данных (решение Р20 (а)): номер, {@code external_id},
 * период действия и признак архива. Полис с ФИО, датой рождения, полом и e-mail (R-05) остаётся
 * сервису Заявок и сюда не относится.
 *
 * <p>Узкий контракт чтения, от которого зависит контроллер: ему не видны ни R-05, ни другие справочники.
 */
public interface PolicyContractViews {

    /**
     * Полис по нашему {@code id}; архивный отдаётся с признаком, а не как «не найден».
     *
     * @throws ReferenceNotFoundException полиса с таким {@code id} нет
     */
    PolicyContractView byId(UUID id);

    /**
     * Полис по GUID 1С:УСК: так сервис Договоров находит наш {@code id} выпущенного полиса.
     *
     * @throws ReferenceNotFoundException полиса с таким {@code external_id} нет
     */
    PolicyContractView byExternalId(UUID externalId);
}
