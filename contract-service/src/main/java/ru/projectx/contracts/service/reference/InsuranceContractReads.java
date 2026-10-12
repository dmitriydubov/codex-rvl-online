package ru.projectx.contracts.service.reference;

import java.util.UUID;
import ru.projectx.contracts.dto.ReferenceInsuranceContract;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceNotFoundException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;

/** Чтение договора страхования страхователя из сервиса справочных данных по его идентификатору в справочнике. */
public interface InsuranceContractReads {

    /**
     * Живой договор страхования по идентификатору справочника.
     *
     * @throws ReferenceNotFoundException договора с таким идентификатором нет
     * @throws ReferenceArchivedException договор помечен в 1С:УСК на удаление
     * @throws ReferenceUnavailableException справочник не ответил и после повторов
     */
    ReferenceInsuranceContract insuranceContract(UUID id);
}
