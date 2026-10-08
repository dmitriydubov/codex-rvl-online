package ru.projectx.datareference.service.onec;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.projectx.datareference.entity.InboundMessage;

/**
 * Последняя применённая передача прайса по услуге. Новее та передача, финальное сообщение которой принято позже: так
 * 1С:УСК заканчивает передачи, а порядок сборки ничего не говорит. При равных моментах побеждает собравшаяся позже.
 */
record AppliedPriceTransfer(UUID service, UUID cid, Instant finalReceivedAt) {

    /** Применённая передача новее передачи, финальное сообщение которой принято в {@code otherFinalReceivedAt}. */
    boolean isNewerThan(Instant otherFinalReceivedAt) {
        return finalReceivedAt.isAfter(otherFinalReceivedAt);
    }

    /**
     * Зависшая передача уже не может стать последней: все её части пришли раньше финала применённой, или её
     * собственный финал старше.
     *
     * @param stuckParts части зависшей передачи, которые ещё лежат в буфере
     */
    boolean supersedes(List<InboundMessage> stuckParts) {
        return !stuckParts.isEmpty()
                && (stuckParts.stream().allMatch(this::receivedEarlier) || stuckParts.stream().anyMatch(this::isEarlierFinal));
    }

    /** Причина пропуска передачи, которая старше применённой. */
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
