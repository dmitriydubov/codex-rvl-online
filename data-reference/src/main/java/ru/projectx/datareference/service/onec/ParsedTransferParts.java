package ru.projectx.datareference.service.onec;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.datareference.entity.InboundMessage;
import tools.jackson.databind.node.ObjectNode;

/**
 * Поле {@code data} частей передачи прайса, разобранное заранее, до блокировки строки услуги. Части, которой здесь нет,
 * сборка разбирает сама.
 *
 * @param dataByMessage разобранное поле {@code data} по идентификатору сообщения части
 */
record ParsedTransferParts(Map<UUID, ObjectNode> dataByMessage) {

    /** Заранее не разобрано ничего: передача не выглядела собранной. */
    static final ParsedTransferParts NONE = new ParsedTransferParts(Map.of());

    ParsedTransferParts {
        dataByMessage = Map.copyOf(dataByMessage);
    }

    Optional<ObjectNode> dataOf(InboundMessage part) {
        return Optional.ofNullable(dataByMessage.get(part.receipt().messageId()));
    }
}
