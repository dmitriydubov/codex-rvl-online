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
 * Кэши ответов справочных ручек в памяти процесса (Caffeine).
 *
 * <p>Кэш срабатывает раньше транзакции чтения: у кэша порядок на единицу выше, чем у транзакций, у которых
 * порядок по умолчанию самый низкий. Поэтому ответ из кэша не открывает транзакцию и не берёт соединение
 * из пула, и отдаётся, даже когда база не отвечает.
 *
 * <p>Набор кэшей постоянный ({@link ReferenceCaches#ALL}): кэш с именем не из списка не создаётся на лету, а
 * опечатка в имени роняет вызов. Пустых ответов в кэше нет: ни один кэшируемый метод не возвращает
 * {@code null}. У каждого кэша ведётся статистика попаданий и промахов — по ней строятся метрики кэша.
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
