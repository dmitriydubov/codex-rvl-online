package ru.projectx.contracts.service.reference;

import java.util.UUID;
import ru.projectx.contracts.dto.ReferencePolicy;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceNotFoundException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;

/**
 * Чтение полиса без персональных данных из сервиса справочных данных: по идентификатору справочника, когда полис
 * известен из задачи на выпуск, и по идентификатору 1С:УСК, когда о выпущенном полисе только что сообщила 1С.
 */
public interface PolicyReads {

    /**
     * Живой полис по идентификатору справочника.
     *
     * @throws ReferenceNotFoundException полиса с таким идентификатором нет
     * @throws ReferenceArchivedException полис помечен в 1С:УСК на удаление
     * @throws ReferenceUnavailableException справочник не ответил и после повторов
     */
    ReferencePolicy policy(UUID id);

    /**
     * Живой полис по идентификатору 1С:УСК. Полис может ещё идти из 1С в справочник, поэтому ответ «не найден»
     * повторяется ограниченное число раз и после исчерпания повторов считается недоступностью справочника.
     *
     * @throws ReferenceArchivedException полис помечен в 1С:УСК на удаление
     * @throws ReferenceUnavailableException справочник не ответил или полис не дошёл до него за отведённые попытки
     */
    ReferencePolicy policyByExternalId(UUID externalId);
}
