package ru.projectx.datareference.service;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.InboxEntry;
import ru.projectx.datareference.repository.InboxRepository;

/**
 * Дедупликация сообщений 1С по {@code metadata.message-id}.
 *
 * <p>Зачем она нужна: доставка из RabbitMQ — <b>at-least-once</b>. Сообщение, по которому
 * мы не успели подтвердить получение (упал сервис, оборвалось соединение, истёк таймаут),
 * брокер пришлёт заново. Без этой проверки повторная доставка означала бы повторное
 * применение — а часть потоков 1С замещает табличные части целиком, и «применить дважды»
 * там не безобидно.
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
     * Запоминает сообщение, если такого ещё не было.
     *
     * <p>Транзакция — вызывающего: при приёме сообщения запись в журнал и сохранение сырья в
     * {@code inbound_message} обязаны попасть в одну транзакцию. Сообщение, отмеченное принятым,
     * но не сохранённое целиком, потеряно навсегда: брокер его больше не пришлёт,
     * а дедупликация уже считает его известным.
     *
     * <p>Гонку двух параллельных доставок одного {@code message-id} эта проверка
     * не закрывает — её закрывает первичный ключ таблицы. Обе доставки видят пустой
     * журнал, обе вставляют, вторая падает на ключе, её транзакция откатывается целиком,
     * брокер передоставляет сообщение, и на следующем круге проверка уже возвращает
     * {@code false}. Ловить это исключение здесь нечем: откатить нужно и запись сырья,
     * а решение «подтвердить или вернуть» принимает слушатель.
     *
     * @return {@code true} — сообщение новое и записано в журнал; {@code false} — дубль,
     *         обрабатывать его не нужно, брокеру достаточно подтверждения
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
