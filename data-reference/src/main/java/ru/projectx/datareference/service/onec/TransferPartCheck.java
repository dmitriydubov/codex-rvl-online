package ru.projectx.datareference.service.onec;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

final class TransferPartCheck {

    private static final String RECORD_SET = "recordSet";

    private final Consumer<JsonNode> rowCheck;

    TransferPartCheck(Consumer<JsonNode> rowCheck) {
        this.rowCheck = rowCheck;
    }

    void check(UUID messageId, TransferMarkup markup, ObjectNode data, List<InboundMessage> parts) {
        if (CorrelatedTransfer.isClosed(parts)) {
            return;
        }
        if (markup.batchCount() == null) {
            checkBatch(markup, data, parts);
            return;
        }
        checkSingleFinal(messageId, markup, parts);
    }

    private void checkBatch(TransferMarkup markup, ObjectNode data, List<InboundMessage> parts) {
        StreamSupport.stream(data.path(RECORD_SET).spliterator(), false).forEach(this::checkRow);
        if (isApplied(parts)) {
            throw new OneCFieldException("лишний пакет: передача %s уже применена".formatted(markup.cid()));
        }
    }

    private static void checkSingleFinal(UUID messageId, TransferMarkup markup, List<InboundMessage> parts) {
        if (parts.stream().anyMatch(part -> isAnotherFinal(part, messageId))) {
            throw new OneCFieldException(secondFinalReason(parts).formatted(markup.cid()));
        }
    }

    private static boolean isAnotherFinal(InboundMessage part, UUID messageId) {
        return part.transferPart().batchCount() != null && !part.receipt().messageId().equals(messageId);
    }

    private static String secondFinalReason(List<InboundMessage> parts) {
        return isApplied(parts)
                ? "второе финальное сообщение: передача %s уже применена"
                : "второе финальное сообщение передачи %s";
    }

    private static boolean isApplied(List<InboundMessage> parts) {
        return parts.stream().anyMatch(part -> part.outcome().status() == InboundStatus.PROCESSED);
    }

    private void checkRow(JsonNode row) {
        TransferRowNumber.of(row);
        rowCheck.accept(row);
    }
}
