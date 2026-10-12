package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.entity.OneCStream;

class ReferenceCacheEvictionTest {

    private static final String KEY = "ключ";
    private static final String ANSWER = "ответ";

    private final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
    private final ReferenceCacheEviction eviction = new ReferenceCacheEviction(cacheManager);

    @BeforeEach
    void fillEveryCache() {
        cacheManager.setCacheNames(ReferenceCaches.ALL);
        ReferenceCaches.ALL.forEach(name -> cache(name).put(KEY, ANSWER));
    }

    @Test
    void mkb10Message_clearsBothMkb10Caches_leavesTheRest() {
        eviction.onApplied(new OneCMessageApplied(OneCStream.MKB10));

        Map<String, Boolean> filled = ReferenceCaches.ALL.stream()
                .collect(Collectors.toMap(Function.identity(), name -> cache(name).get(KEY) != null));
        assertThat(filled)
                .containsEntry(ReferenceCaches.MKB10_SEARCH, false)
                .containsEntry(ReferenceCaches.MKB10_CODES, false)
                .containsEntry(ReferenceCaches.PROGRAMS, true)
                .containsEntry(ReferenceCaches.LPU_CATALOG, true)
                .containsEntry(ReferenceCaches.INSURANCE_CONTRACTS_BY_ID, true);
    }

    @Test
    void priceListMessage_clearsEveryEntryOfItsCaches() {
        cache(ReferenceCaches.SERVICE_PRICES).put("другой ключ", ANSWER);

        eviction.onApplied(new OneCMessageApplied(OneCStream.PRICE_LISTS));

        assertThat(cache(ReferenceCaches.SERVICE_PRICES).get("другой ключ")).isNull();
        assertThat(cache(ReferenceCaches.LPU_SERVICES_WITH_PRICES).get(KEY)).isNull();
        assertThat(cache(ReferenceCaches.LPU_SERVICES).get(KEY)).isNotNull();
    }

    private Cache cache(String name) {
        return cacheManager.getCache(name);
    }
}
