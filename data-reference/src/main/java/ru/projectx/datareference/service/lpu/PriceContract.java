package ru.projectx.datareference.service.lpu;

import ru.projectx.datareference.dto.PriceContractRef;
import ru.projectx.datareference.entity.catalog.LpuContract;

public record PriceContract(LpuContract contract, PriceContractRef.Kind kind) {

    public static PriceContract of(LpuContract contract) {
        return new PriceContract(contract,
                contract.mainContractExternalId() == null ? PriceContractRef.Kind.MAIN : PriceContractRef.Kind.DOPS);
    }
}
