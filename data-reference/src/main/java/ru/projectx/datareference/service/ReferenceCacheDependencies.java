package ru.projectx.datareference.service;

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

/**
 * Из каких потоков 1С:УСК собирается ответ каждого кэша, чтобы сообщение сбрасывало ровно зависящие от него кэши.
 * Лишний сброс сводит кэш на нет, потому что полисы приходят десятками тысяч, а пропущенный оставляет старый ответ.
 * Каталог зависит от полисов, хотя их в нём не видно: он строится по программам действующих полисов договора.
 */
final class ReferenceCacheDependencies {

    private static final Map<String, Set<OneCStream>> STREAMS_BY_CACHE = Map.of(
            LPU_CATALOG, Set.of(INSURANCE_CONTRACTS, POLICIES, OneCStream.PROGRAMS, CONTRAGENTS, LPU_CONTRACTS,
                    APPEAL_TYPES, LPU_CONTACTS),
            LPU_CARD, Set.of(CONTRAGENTS, LPU_CONTRACTS, LPU_CONTACTS),
            LPU_SERVICES, Set.of(CONTRAGENTS, LPU_CONTRACTS, APPEAL_TYPES, CONTRAGENT_SERVICES),
            LPU_SERVICES_WITH_PRICES, Set.of(CONTRAGENTS, LPU_CONTRACTS, APPEAL_TYPES, CONTRAGENT_SERVICES,
                    PRICE_LISTS),
            SERVICE_PRICES, Set.of(CONTRAGENTS, LPU_CONTRACTS, APPEAL_TYPES, CONTRAGENT_SERVICES, PRICE_LISTS),
            MKB10_SEARCH, Set.of(MKB10),
            MKB10_CODES, Set.of(MKB10),
            INSURANCE_CONTRACTS_BY_ID, Set.of(INSURANCE_CONTRACTS, CONTRAGENTS),
            INSURANCE_CONTRACTS_BY_EXTERNAL_ID, Set.of(INSURANCE_CONTRACTS, CONTRAGENTS),
            PROGRAMS, Set.of(OneCStream.PROGRAMS, CONTRAGENTS));

    private ReferenceCacheDependencies() {
    }

    /** Кэши, ответы которых собираются из таблиц этого потока. */
    static Set<String> cachesOf(OneCStream stream) {
        return STREAMS_BY_CACHE.entrySet().stream()
                .filter(entry -> entry.getValue().contains(stream))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
