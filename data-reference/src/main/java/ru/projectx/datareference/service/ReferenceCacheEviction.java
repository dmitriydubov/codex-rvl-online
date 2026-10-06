package ru.projectx.datareference.service;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Сбрасывает кэши ответов, которые зависят от потока применённого сообщения 1С:УСК.
 *
 * <p>Сброс идёт после фиксации транзакции разбора, а не внутри неё: чтение, начатое между сбросом и фиксацией,
 * увидело бы ещё старые данные и снова положило бы их в кэш. Откат транзакции сброса не вызывает: в базе ничего
 * не изменилось. Кэш сбрасывается целиком: по сообщению не узнать, каких именно ответов оно касается, — договор
 * ЛПУ меняет каталог всех договоров страхования, где есть это учреждение.
 *
 * <p>Чего сброс не закрывает: чтение, которое прочитало базу до фиксации, а положило ответ в кэш уже после сброса,
 * оставит в кэше старый ответ. Такое бывает, только когда чтение идёт ровно в момент фиксации, и живёт такой ответ
 * не дольше срока жизни кэша. Ошибка самого сброса пишется в журнал и разбор не отменяет: изменения уже
 * зафиксированы, а старый ответ уйдёт по сроку жизни.
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
