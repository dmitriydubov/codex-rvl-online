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
 * Обычный путь сообщения 1С: маппер потока разбирает его, а исход записывается в ту же строку сырья. Применённое или
 * пропущенное сообщение закрывает более старые сообщения той же записи, которые ещё ждут родителя, потому что в
 * справочнике действует последнее полученное.
 */
class MappedStreamHandler implements StreamHandler {

    private static final String APPLIED = "применено";
    private static final String SKIPPED = "пропущено";

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
            case ImportResult.Applied ignored -> {
                inboundMessageService.markProcessed(messageId);
                yield closingOlderWaits(message, data, APPLIED, ImportOutcome.applied());
            }
            case ImportResult.Skipped skipped -> {
                inboundMessageService.markSkipped(messageId, skipped.reason());
                yield closingOlderWaits(message, data, SKIPPED, ImportOutcome.skipped(skipped.reason()));
            }
            case ImportResult.AwaitingParent awaiting -> {
                inboundMessageService.markAwaitingParent(messageId, awaiting.parent(), mapper.recordOf(data).orElse(null));
                yield ImportOutcome.awaitingParent(awaiting.parent().waitingReason());
            }
        };
    }

    private ImportOutcome closingOlderWaits(
            InboundMessage message, ObjectNode data, String newerOutcome, ImportOutcome outcome) {

        mapper.recordOf(data)
                .ifPresent(record -> supersededWaits.closeOlderThan(message.receipt(), record, newerOutcome));
        return outcome;
    }
}
