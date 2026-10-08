package ru.projectx.datareference.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Кэши ответов справочных ручек в памяти процесса (Caffeine). Набор кэшей постоянный: опечатка в имени роняет вызов,
 * а не создаёт новый кэш. Кэш срабатывает раньше транзакции чтения, поэтому ответ из кэша не берёт соединение из пула
 * и отдаётся, даже когда база не отвечает.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(ReferenceCacheProperties properties, Clock clock) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(false);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(properties.ttl())
                .maximumSize(properties.maximumSize())
                .ticker(new ClockTicker(clock))
                .recordStats());
        manager.setCacheNames(ReferenceCaches.ALL);
        return manager;
    }
}
