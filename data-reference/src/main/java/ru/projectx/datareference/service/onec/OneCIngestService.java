package ru.projectx.datareference.service.onec;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.service.inbound.InboxService;
import ru.projectx.datareference.service.inbound.InboundMessageService;

@Service
public class OneCIngestService {

    private final InboxService inboxService;
    private final InboundMessageService inboundMessageService;

    public OneCIngestService(InboxService inboxService, InboundMessageService inboundMessageService) {
        this.inboxService = inboxService;
        this.inboundMessageService = inboundMessageService;
    }

    @Transactional
    public boolean receive(UUID messageId, OneCStream stream, String sourceQueue, String payload) {
        if (!inboxService.registerIfNew(messageId)) {
            return false;
        }
        inboundMessageService.store(messageId, stream, sourceQueue, payload);
        return true;
    }
}
