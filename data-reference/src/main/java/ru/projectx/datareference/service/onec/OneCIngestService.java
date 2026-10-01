package ru.projectx.datareference.service.onec;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.InboxService;
import ru.projectx.datareference.service.InboundMessageService;

/**
 * Приём одного сообщения из 1С: отметить в журнале входящих и сохранить сырьё.
 *
 * <p><b>Одной транзакцией, и это главное здесь.</b> Сообщение, отмеченное принятым,
 * но не сохранённое, потеряно навсегда: брокер его больше не пришлёт (мы подтвердили
 * получение), а дедупликация уже считает его известным. Обратное разъединение не лучше:
 * сохранённое без отметки сырьё применится повторно при первой же передоставке.
 *
 * <p>Разбора здесь нет вовсе — тело сохраняется как есть. Разбор отделён от приёма
 * намеренно: сообщение, которое не удалось разложить по таблицам, остаётся в базе
 * целиком, и починить маппер можно, не запрашивая выгрузку из 1С заново.
 */
@Service
public class OneCIngestService {

    private final InboxService inboxService;
    private final InboundMessageService inboundMessageService;

    public OneCIngestService(InboxService inboxService, InboundMessageService inboundMessageService) {
        this.inboxService = inboxService;
        this.inboundMessageService = inboundMessageService;
    }

    /**
     * Отмечает сообщение во входящих и, если оно новое, сохраняет сырьё. Исключение
     * откатывает обе записи; слушатель тогда возвращает сообщение в очередь.
     *
     * @param messageId   {@code metadata.message-id} конверта — ключ дедупликации и строки сырья
     * @param stream      поток 1С, которому принадлежит очередь
     * @param sourceQueue очередь, из которой пришло сообщение: у прайсов именно она
     *                    отличает пакет строк от финального сообщения
     * @param payload     тело сообщения целиком, без разбора
     * @return {@code true} — сообщение новое и сохранено; {@code false} — дубль,
     *         обрабатывать нечего, брокеру достаточно подтверждения
     */
    @Transactional
    public boolean receive(UUID messageId, OneCStream stream, String sourceQueue, String payload) {
        if (!inboxService.registerIfNew(messageId)) {
            return false;
        }
        inboundMessageService.store(messageId, stream, sourceQueue, payload);
        return true;
    }
}
