package ru.projectx.datareference.service;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Сбрасывает целиком кэши, зависящие от потока разобранного сообщения 1С:УСК, после фиксации разбора: сброс внутри
 * транзакции дал бы чтению снова положить в кэш старые данные. Целиком, потому что по сообщению не узнать, каких
 * ответов оно касается. Чтение, совпавшее с фиксацией, может оставить старый ответ до конца срока жизни кэша.
 */
@Component
public class ReferenceCacheEviction {

    private static final Logger log = LoggerFactory.getLogger(ReferenceCacheEviction.class);

    private final CacheManager cacheManager;

    public ReferenceCacheEviction(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener
    public void onApplied(OneCMessageApplied event) {
        ReferenceCacheDependencies.cachesOf(event.stream()).forEach(this::clear);
    }

    private void clear(String cacheName) {
        Objects.requireNonNull(cacheManager.getCache(cacheName), cacheName).invalidate();
        log.debug("кэш {} сброшен", cacheName);
    }
}
