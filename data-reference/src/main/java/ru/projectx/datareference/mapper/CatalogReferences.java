package ru.projectx.datareference.mapper;

import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.catalog.ContragentService;
import ru.projectx.datareference.entity.catalog.Lpu;
import ru.projectx.datareference.entity.catalog.LpuContract;
import ru.projectx.datareference.entity.inbound.ParentKind;
import ru.projectx.datareference.repository.catalog.ContragentServiceRepository;
import ru.projectx.datareference.repository.catalog.LpuContractRepository;
import ru.projectx.datareference.repository.catalog.LpuRepository;
import tools.jackson.databind.JsonNode;

@Component
public class CatalogReferences {

    private final LpuRepository lpus;
    private final LpuContractRepository contracts;
    private final ContragentServiceRepository services;
    private final ForeignReferences foreign;

    public CatalogReferences(LpuRepository lpus, LpuContractRepository contracts, ContragentServiceRepository services,
                      ForeignReferences foreign) {
        this.lpus = lpus;
        this.contracts = contracts;
        this.services = services;
        this.foreign = foreign;
    }

    public CatalogReference<Lpu> sanatoriums(String outsideCatalog) {
        return new CatalogReference<>(lpus::findByExternalId, ParentKind.CONTRAGENT, foreign, outsideCatalog);
    }

    public CatalogReference<LpuContract> contracts(String outsideCatalog) {
        return new CatalogReference<>(contracts::findByExternalId, ParentKind.LPU_CONTRACT, foreign, outsideCatalog);
    }

    public CatalogReference<ContragentService> services(String outsideCatalog) {
        return new CatalogReference<>(services::findByExternalId, ParentKind.CONTRAGENT_SERVICE, foreign,
                outsideCatalog);
    }

    ImportResult rememberIfSkipped(ImportResult result, ParentKind kind, JsonNode data) {
        return foreign.rememberIfSkipped(result, kind, data);
    }
}
