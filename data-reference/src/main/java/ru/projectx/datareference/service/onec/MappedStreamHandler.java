package ru.projectx.datareference.service.onec;

import java.util.UUID;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.mapper.StreamMapper;
import ru.projectx.datareference.service.InboundMessageService;
import tools.jackson.databind.node.ObjectNode;

/**
 * Обычный путь сообщения 1С: маппер потока разбирает его, а исход записывается в ту же строку сырья.
 *
 * <p>Применённое сообщение отмечается обработанным и закрывает более старые сообщения той же записи, которые ещё ждут
 * родителя. Пропущенное отмечается пропущенным с причиной от маппера. Сообщение, которое ссылается на ещё не
 * пришедшую запись, ждёт её. Так обрабатываются все потоки, у которых одно сообщение применяется само по себе.
 */
class MappedStreamHandler implements StreamHandler {

    private final StreamMapper mapper;
    private final InboundMessageService inboundMessageService;
    private final SupersededParentWaits supersededWaits;

    MappedStreamHandler(
            StreamMapper mapper, InboundMessageService inboundMessageService, SupersededParentWaits supersededWaits) {

        this.mapper = mapper;
        this.inboundMessageService = inboundMessageService;
        this.supersededWaits = supersededWaits;
    }

    @Override
    public OneCStream stream() {
        return mapper.stream();
    }

    /** @throws OneCFieldException если сообщение нарушает контракт */
    @Override
    public ImportOutcome handle(InboundMessage message, ObjectNode data) {
        UUID messageId = message.receipt().messageId();
        return switch (mapper.apply(data)) {
            case ImportResult.Applied ignored -> applied(message, data);
            case ImportResult.Skipped skipped -> {
                inboundMessageService.markSkipped(messageId, skipped.reason());
                yield ImportOutcome.skipped(skipped.reason());
            }
            case ImportResult.AwaitingParent awaiting -> {
                inboundMessageService.markAwaitingParent(messageId, awaiting.parent(), mapper.recordOf(data).orElse(null));
                yield ImportOutcome.awaitingParent(awaiting.parent().waitingReason());
            }
        };
    }

    private ImportOutcome applied(InboundMessage message, ObjectNode data) {
        inboundMessageService.markProcessed(message.receipt().messageId());
        mapper.recordOf(data).ifPresent(record -> supersededWaits.closeOlderThan(message.receipt(), record));
        return ImportOutcome.applied();
    }
}
