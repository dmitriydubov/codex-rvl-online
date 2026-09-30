package ru.projectx.datareference.service;

import ru.projectx.datareference.dto.PriceContractRef;
import ru.projectx.datareference.entity.LpuContract;

/**
 * Итог базовой логики B: договор ЛПУ, по которому берутся услуги и цены, и его вид.
 * Вид нужен вызывающему: цены с допсоглашения и цены с главного договора — первый
 * вопрос при разборе расхождений по суммам.
 */
public record PriceContract(LpuContract contract, PriceContractRef.Kind kind) {

    /** Допсоглашение отличает от главного договора только ссылка на главный. */
    static PriceContract of(LpuContract contract) {
        return new PriceContract(contract,
                contract.mainContractExternalId() == null ? PriceContractRef.Kind.MAIN : PriceContractRef.Kind.DOPS);
    }
}
