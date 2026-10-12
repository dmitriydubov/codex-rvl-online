package ru.projectx.datareference.service.onec;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.projectx.datareference.entity.InboundMessage;

record AppliedPriceTransfer(UUID service, UUID cid, Instant finalReceivedAt) {

    boolean isNewerThan(Instant otherFinalReceivedAt) {
        return finalReceivedAt.isAfter(otherFinalReceivedAt);
    }

    boolean supersedes(List<InboundMessage> stuckParts) {
        return !stuckParts.isEmpty()
                && (stuckParts.stream().allMatch(this::receivedEarlier) || stuckParts.stream().anyMatch(this::isEarlierFinal));
    }

    String reason() {
        return "по услуге %s уже применена более новая передача %s".formatted(service, cid);
    }

    private boolean receivedEarlier(InboundMessage part) {
        return isNewerThan(part.receipt().receivedAt());
    }

    private boolean isEarlierFinal(InboundMessage part) {
        return part.transferPart().batchCount() != null && receivedEarlier(part);
    }
}
