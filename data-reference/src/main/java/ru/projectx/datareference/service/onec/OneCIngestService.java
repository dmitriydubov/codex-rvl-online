package ru.projectx.datareference.service.onec;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.InboxService;
import ru.projectx.datareference.service.InboundMessageService;

/**
 * Приём одного сообщения из 1С: отметка во входящих и сохранение тела как есть, без разбора, одной транзакцией.
 * Отмеченное, но не сохранённое сообщение потеряно навсегда, а сохранённое без отметки применится повторно при
 * передоставке. Разбор отделён от приёма, чтобы неразобранное сообщение можно было переобработать без выгрузки из 1С.
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
     * @param sourceQueue очередь, из которой пришло сообщение: у прайсов она отличает пакет строк от финального
     * @return {@code false} для дубля: обрабатывать нечего, брокеру достаточно подтверждения
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
