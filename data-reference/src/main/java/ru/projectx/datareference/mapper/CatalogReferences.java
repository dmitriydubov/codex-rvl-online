package ru.projectx.datareference.mapper;

import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.LpuContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import tools.jackson.databind.JsonNode;

@Component
class CatalogReferences {

    private final LpuRepository lpus;
    private final LpuContractRepository contracts;
    private final ContragentServiceRepository services;
    private final ForeignReferences foreign;

    CatalogReferences(LpuRepository lpus, LpuContractRepository contracts, ContragentServiceRepository services,
                      ForeignReferences foreign) {
        this.lpus = lpus;
        this.contracts = contracts;
        this.services = services;
        this.foreign = foreign;
    }

    CatalogReference<Lpu> sanatoriums(String outsideCatalog) {
        return new CatalogReference<>(lpus::findByExternalId, ParentKind.CONTRAGENT, foreign, outsideCatalog);
    }

    CatalogReference<LpuContract> contracts(String outsideCatalog) {
        return new CatalogReference<>(contracts::findByExternalId, ParentKind.LPU_CONTRACT, foreign, outsideCatalog);
    }

    CatalogReference<ContragentService> services(String outsideCatalog) {
        return new CatalogReference<>(services::findByExternalId, ParentKind.CONTRAGENT_SERVICE, foreign,
                outsideCatalog);
    }

    ImportResult rememberIfSkipped(ImportResult result, ParentKind kind, JsonNode data) {
        return foreign.rememberIfSkipped(result, kind, data);
    }
}
