package ru.projectx.datareference.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import ru.projectx.datareference.config.cache.ReferenceCaches;
import ru.projectx.datareference.entity.inbound.OneCStream;

class StreamCacheSubscriptionTest {

    private static final String KEY = "ключ";
    private static final String ANSWER = "ответ";

    private final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
    private final StreamCacheSubscription subscription =
            new StreamCacheSubscription(OneCStream.MKB10, new ReferenceCacheEviction(cacheManager));

    @BeforeEach
    void fillEveryCache() {
        cacheManager.setCacheNames(ReferenceCaches.ALL);
        ReferenceCaches.ALL.forEach(name -> cache(name).put(KEY, ANSWER));
    }

    @Test
    void subscribesToOurRegionAndItsStream() {
        assertThat(subscription.getRegion()).isEqualTo(ReferenceCacheChannel.REGION);
        assertThat(subscription.getGroupId()).isEqualTo("MKB10");
    }

    @Test
    void notification_clearsCachesOfItsStream_leavesTheRest() {
        subscription.notifyUpdate();

        assertThat(cache(ReferenceCaches.MKB10_CODES).get(KEY)).isNull();
        assertThat(cache(ReferenceCaches.MKB10_SEARCH).get(KEY)).isNull();
        assertThat(cache(ReferenceCaches.INSURANCE_CONTRACTS_BY_ID).get(KEY)).isNotNull();
    }

    private Cache cache(String name) {
        return cacheManager.getCache(name);
    }
}
