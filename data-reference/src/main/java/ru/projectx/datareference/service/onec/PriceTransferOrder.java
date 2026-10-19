package ru.projectx.datareference.service.onec;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.repository.inbound.PriceListFinalizationRepository;

@Component
public class PriceTransferOrder {

    private final PriceListFinalizationRepository finalizations;
    private final InboundMessageRepository inbound;

    public PriceTransferOrder(PriceListFinalizationRepository finalizations, InboundMessageRepository inbound) {
        this.finalizations = finalizations;
        this.inbound = inbound;
    }

    ImportResult applyInOrder(UUID service, InboundMessage finalMessage, Supplier<ImportResult> replacement) {
        return lockApplied(service)
                .filter(applied -> applied.isNewerThan(finalMessage.receipt().receivedAt()))
                .map(newer -> ImportResult.skipped(newer.reason()))
                .orElseGet(() -> replaceAndRemember(service, finalMessage, replacement));
    }

    Optional<AppliedPriceTransfer> lockApplied(UUID service) {
        return finalizations.lockByService(service)
                .map(finalization -> appliedBy(service, finalization.finalMessageId()));
    }

    private ImportResult replaceAndRemember(
            UUID service, InboundMessage finalMessage, Supplier<ImportResult> replacement) {

        ImportResult result = replacement.get();
        if (result instanceof ImportResult.Applied) {
            remember(service, finalMessage.receipt().messageId());
        }
        return result;
    }

    private void remember(UUID service, UUID finalMessageId) {
        finalizations.recordApplied(service, finalMessageId);
    }

    private AppliedPriceTransfer appliedBy(UUID service, UUID finalMessageId) {
        InboundMessage finalMessage = inbound.findById(finalMessageId)
                .orElseThrow(() -> new IllegalStateException(
                        "нет строки сырья финального сообщения %s применённой передачи".formatted(finalMessageId)));
        return new AppliedPriceTransfer(service, Objects.requireNonNull(finalMessage.transferPart().correlationId()),
                finalMessage.receipt().receivedAt());
    }
}
