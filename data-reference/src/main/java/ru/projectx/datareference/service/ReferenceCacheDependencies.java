package ru.projectx.datareference.service;

import static java.util.Map.entry;
import static ru.projectx.datareference.config.ReferenceCaches.ACTIVE_INSURANCE_CONTRACTS_BY_INSURER;
import static ru.projectx.datareference.config.ReferenceCaches.INSURANCE_CONTRACTS_BY_EXTERNAL_ID;
import static ru.projectx.datareference.config.ReferenceCaches.INSURANCE_CONTRACTS_BY_ID;
import static ru.projectx.datareference.config.ReferenceCaches.LPU_CARD;
import static ru.projectx.datareference.config.ReferenceCaches.LPU_CATALOG;
import static ru.projectx.datareference.config.ReferenceCaches.LPU_SERVICES;
import static ru.projectx.datareference.config.ReferenceCaches.LPU_SERVICES_WITH_PRICES;
import static ru.projectx.datareference.config.ReferenceCaches.MKB10_CODES;
import static ru.projectx.datareference.config.ReferenceCaches.MKB10_SEARCH;
import static ru.projectx.datareference.config.ReferenceCaches.PROGRAMS;
import static ru.projectx.datareference.config.ReferenceCaches.SERVICE_PRICES;
import static ru.projectx.datareference.entity.OneCStream.APPEAL_TYPES;
import static ru.projectx.datareference.entity.OneCStream.CONTRAGENTS;
import static ru.projectx.datareference.entity.OneCStream.CONTRAGENT_SERVICES;
import static ru.projectx.datareference.entity.OneCStream.INSURANCE_CONTRACTS;
import static ru.projectx.datareference.entity.OneCStream.LPU_CONTACTS;
import static ru.projectx.datareference.entity.OneCStream.LPU_CONTRACTS;
import static ru.projectx.datareference.entity.OneCStream.MKB10;
import static ru.projectx.datareference.entity.OneCStream.POLICIES;
import static ru.projectx.datareference.entity.OneCStream.PRICE_LISTS;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import ru.projectx.datareference.entity.OneCStream;

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
