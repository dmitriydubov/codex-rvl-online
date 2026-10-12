package ru.projectx.datareference.service;

import ru.projectx.datareference.dto.PriceContractRef;
import ru.projectx.datareference.entity.LpuContract;

public record PriceContract(LpuContract contract, PriceContractRef.Kind kind) {

    static PriceContract of(LpuContract contract) {
        return new PriceContract(contract,
                contract.mainContractExternalId() == null ? PriceContractRef.Kind.MAIN : PriceContractRef.Kind.DOPS);
    }
}
