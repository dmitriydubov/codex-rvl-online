package ru.projectx.datareference.service;

import ru.projectx.datareference.dto.PriceContractRef;
import ru.projectx.datareference.entity.LpuContract;

/**
 * Прайсовый договор учреждения, по которому берутся услуги и цены, и его вид. Вид отдаётся вызывающему, потому что
 * при разборе расхождений по суммам первым делом спрашивают, взяты цены с допсоглашения или с главного договора.
 */
public record PriceContract(LpuContract contract, PriceContractRef.Kind kind) {

    /** Допсоглашение отличается от главного договора только ссылкой на главный. */
    static PriceContract of(LpuContract contract) {
        return new PriceContract(contract,
                contract.mainContractExternalId() == null ? PriceContractRef.Kind.MAIN : PriceContractRef.Kind.DOPS);
    }
}
