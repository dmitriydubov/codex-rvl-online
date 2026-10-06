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

/**
 * Ссылки потоков 1С на родительские записи справочника: санаторий, договор санатория, услугу. Каждая ссылка знает три
 * исхода: родитель есть, родитель признан не нашим, о родителе ещё ничего не известно.
 *
 * <p>Собраны в одном месте, чтобы поиск родителя и память о не наших записях у всех потоков были одними и теми же.
 */
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

    /** @param outsideCatalog начало причины пропуска сообщения, которое ссылается на не наш санаторий */
    CatalogReference<Lpu> sanatoriums(String outsideCatalog) {
        return new CatalogReference<>(lpus::findByExternalId, ParentKind.CONTRAGENT, foreign, outsideCatalog);
    }

    /** @param outsideCatalog начало причины пропуска сообщения, которое ссылается на не наш договор */
    CatalogReference<LpuContract> contracts(String outsideCatalog) {
        return new CatalogReference<>(contracts::findByExternalId, ParentKind.LPU_CONTRACT, foreign, outsideCatalog);
    }

    /** @param outsideCatalog начало причины пропуска сообщения, которое ссылается на не нашу услугу */
    CatalogReference<ContragentService> services(String outsideCatalog) {
        return new CatalogReference<>(services::findByExternalId, ParentKind.CONTRAGENT_SERVICE, foreign,
                outsideCatalog);
    }

    /** Запоминает запись пропущенного сообщения как не нашу, см. {@link ForeignReferences#rememberIfSkipped}. */
    ImportResult rememberIfSkipped(ImportResult result, ParentKind kind, JsonNode data) {
        return foreign.rememberIfSkipped(result, kind, data);
    }
}
