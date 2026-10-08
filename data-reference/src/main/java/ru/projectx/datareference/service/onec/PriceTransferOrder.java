package ru.projectx.datareference.service.onec;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.PriceListFinalizationRepository;

/**
 * Порядок передач прайса по услуге: цены меняет только передача, финал которой принят не раньше финала применённой.
 * Методы первым делом блокируют строку услуги до конца транзакции вызывающего, поэтому сборка и закрытие зависших
 * передач решают за одну услугу по очереди и не применяют старую передачу поверх новой.
 */
@Component
public class PriceTransferOrder {

    private final PriceListFinalizationRepository finalizations;
    private final InboundMessageRepository inbound;

    public PriceTransferOrder(PriceListFinalizationRepository finalizations, InboundMessageRepository inbound) {
        this.finalizations = finalizations;
        this.inbound = inbound;
    }

    /** Замещает прайс, если по услуге не применена более новая передача; иначе пропуск с причиной. */
    ImportResult applyInOrder(UUID service, InboundMessage finalMessage, Supplier<ImportResult> replacement) {
        return lockApplied(service)
                .filter(applied -> applied.isNewerThan(finalMessage.receipt().receivedAt()))
                .map(newer -> ImportResult.skipped(newer.reason()))
                .orElseGet(() -> replaceAndRemember(service, finalMessage, replacement));
    }

    /** Последняя применённая передача по услуге; строка услуги заблокирована до конца транзакции вызывающего. */
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

    /** Пишет в базу мимо сущности, загруженной блокировкой: после этого её в транзакции не читают. */
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
