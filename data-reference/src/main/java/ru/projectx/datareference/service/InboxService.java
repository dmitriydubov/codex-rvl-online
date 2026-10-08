package ru.projectx.datareference.service;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.InboxEntry;
import ru.projectx.datareference.repository.InboxRepository;

/**
 * Отсеивает повторные доставки сообщений 1С по {@code metadata.message-id}. RabbitMQ доставляет хотя бы один раз,
 * и неподтверждённое сообщение приходит снова, а часть потоков замещает табличные части целиком.
 */
@Service
public class InboxService {

    private final InboxRepository repository;
    private final Clock clock;

    public InboxService(InboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Возвращает {@code false} для дубля. Работает в транзакции вызывающего вместе с записью сырья. Гонку двух
     * одновременных доставок закрывает первичный ключ: вторая падает, откатывается целиком и придёт снова как дубль.
     */
    @Transactional
    public boolean registerIfNew(UUID messageId) {
        if (repository.existsById(messageId)) {
            return false;
        }
        repository.save(InboxEntry.receive(messageId, clock.instant()));
        return true;
    }
}
