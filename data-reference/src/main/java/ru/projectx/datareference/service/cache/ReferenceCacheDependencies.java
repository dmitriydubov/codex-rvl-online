package ru.projectx.datareference.service.cache;

import static java.util.Map.entry;
import static ru.projectx.datareference.config.cache.ReferenceCaches.ACTIVE_INSURANCE_CONTRACTS_BY_INSURER;
import static ru.projectx.datareference.config.cache.ReferenceCaches.INSURANCE_CONTRACTS_BY_EXTERNAL_ID;
import static ru.projectx.datareference.config.cache.ReferenceCaches.INSURANCE_CONTRACTS_BY_ID;
import static ru.projectx.datareference.config.cache.ReferenceCaches.LPU_CARD;
import static ru.projectx.datareference.config.cache.ReferenceCaches.LPU_CATALOG;
import static ru.projectx.datareference.config.cache.ReferenceCaches.LPU_SERVICES;
import static ru.projectx.datareference.config.cache.ReferenceCaches.LPU_SERVICES_WITH_PRICES;
import static ru.projectx.datareference.config.cache.ReferenceCaches.MKB10_CODES;
import static ru.projectx.datareference.config.cache.ReferenceCaches.MKB10_SEARCH;
import static ru.projectx.datareference.config.cache.ReferenceCaches.PROGRAMS;
import static ru.projectx.datareference.config.cache.ReferenceCaches.SERVICE_PRICES;
import static ru.projectx.datareference.entity.inbound.OneCStream.APPEAL_TYPES;
import static ru.projectx.datareference.entity.inbound.OneCStream.CONTRAGENTS;
import static ru.projectx.datareference.entity.inbound.OneCStream.CONTRAGENT_SERVICES;
import static ru.projectx.datareference.entity.inbound.OneCStream.INSURANCE_CONTRACTS;
import static ru.projectx.datareference.entity.inbound.OneCStream.LPU_CONTACTS;
import static ru.projectx.datareference.entity.inbound.OneCStream.LPU_CONTRACTS;
import static ru.projectx.datareference.entity.inbound.OneCStream.MKB10;
import static ru.projectx.datareference.entity.inbound.OneCStream.POLICIES;
import static ru.projectx.datareference.entity.inbound.OneCStream.PRICE_LISTS;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import ru.projectx.datareference.entity.inbound.OneCStream;

final class ReferenceCacheDependencies {

    private static final Map<String, Set<OneCStream>> STREAMS_BY_CACHE = Map.ofEntries(
            entry(LPU_CATALOG, Set.of(INSURANCE_CONTRACTS, POLICIES, OneCStream.PROGRAMS, CONTRAGENTS, LPU_CONTRACTS,
                    APPEAL_TYPES, LPU_CONTACTS)),
            entry(LPU_CARD, Set.of(CONTRAGENTS, LPU_CONTRACTS, LPU_CONTACTS)),
            entry(LPU_SERVICES, Set.of(CONTRAGENTS, LPU_CONTRACTS, APPEAL_TYPES, CONTRAGENT_SERVICES)),
            entry(LPU_SERVICES_WITH_PRICES, Set.of(CONTRAGENTS, LPU_CONTRACTS, APPEAL_TYPES, CONTRAGENT_SERVICES,
                    PRICE_LISTS)),
            entry(SERVICE_PRICES, Set.of(CONTRAGENTS, LPU_CONTRACTS, APPEAL_TYPES, CONTRAGENT_SERVICES, PRICE_LISTS)),
            entry(MKB10_SEARCH, Set.of(MKB10)),
            entry(MKB10_CODES, Set.of(MKB10)),
            entry(INSURANCE_CONTRACTS_BY_ID, Set.of(INSURANCE_CONTRACTS, CONTRAGENTS)),
            entry(INSURANCE_CONTRACTS_BY_EXTERNAL_ID, Set.of(INSURANCE_CONTRACTS, CONTRAGENTS)),
            entry(ACTIVE_INSURANCE_CONTRACTS_BY_INSURER, Set.of(INSURANCE_CONTRACTS, CONTRAGENTS)),
            entry(PROGRAMS, Set.of(OneCStream.PROGRAMS, CONTRAGENTS)));

    private ReferenceCacheDependencies() {
    }

    static Set<String> cachesOf(OneCStream stream) {
        return STREAMS_BY_CACHE.entrySet().stream()
                .filter(entry -> entry.getValue().contains(stream))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
