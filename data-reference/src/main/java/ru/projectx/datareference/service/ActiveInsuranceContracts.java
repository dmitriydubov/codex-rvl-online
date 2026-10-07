package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;

/**
 * Проверка договора страхования перед каталогом учреждений и поиском полисов: обе выдачи строятся только
 * по договору, который действует сегодня.
 *
 * <p>Неизвестный, архивный, истёкший и ещё не начавшийся договор дают один и тот же ответ «не найден»:
 * действующих полисов у такого договора нет, и показывать по нему нечего.
 */
@Component
class ActiveInsuranceContracts {

    private final InsuranceContractRepository insuranceContracts;

    ActiveInsuranceContracts(InsuranceContractRepository insuranceContracts) {
        this.insuranceContracts = insuranceContracts;
    }

    /** Бросает {@link ReferenceNotFoundException}, если договор с этим GUID 1С не действует в день {@code today}. */
    void requireActive(UUID contractExternalId, LocalDate today) {
        insuranceContracts.findByExternalId(contractExternalId)
                .filter(contract -> contract.isActiveOn(today))
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContractByExternalId(contractExternalId));
    }
}
