package ru.projectx.datareference.service.onec;

import java.util.UUID;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.mapper.StreamMapper;
import ru.projectx.datareference.service.InboundMessageService;
import tools.jackson.databind.node.ObjectNode;

/**
 * Обычный путь сообщения 1С: маппер потока разбирает его, а исход записывается в ту же строку сырья.
 *
 * <p>Применённое сообщение отмечается обработанным, пропущенное — пропущенным с причиной от маппера. Так
 * обрабатываются все потоки, у которых одно сообщение применяется само по себе.
 */
class MappedStreamHandler implements StreamHandler {

    private final StreamMapper mapper;
    private final InboundMessageService inboundMessageService;

    MappedStreamHandler(StreamMapper mapper, InboundMessageService inboundMessageService) {
        this.mapper = mapper;
        this.inboundMessageService = inboundMessageService;
    }

    @Override
    public OneCStream stream() {
        return mapper.stream();
    }

    @Override
    public ImportResult handle(InboundMessage message, ObjectNode data) {
        ImportResult result = mapper.apply(data);
        UUID messageId = message.receipt().messageId();
        switch (result) {
            case ImportResult.Applied ignored -> inboundMessageService.markProcessed(messageId);
            case ImportResult.Skipped skipped -> inboundMessageService.markSkipped(messageId, skipped.reason());
        }
        return result;
    }
}
