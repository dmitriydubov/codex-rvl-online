package ru.projectx.datareference.service.onec;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import tools.jackson.databind.node.ObjectNode;

/**
 * Проверка, что часть передачи прайса называет ту же услугу, что части в буфере: иначе не узнать, к какой услуге
 * применить цены. Читаются только GUID услуги, поэтому проверка идёт раньше блокировки строки услуги. Поздняя часть
 * передачи с записанным исходом разделяет его, и её услуга не проверяется.
 */
final class PriceTransferServiceCheck {

    private static final Set<InboundStatus> RECORDED_OUTCOME =
            EnumSet.of(InboundStatus.PROCESSED, InboundStatus.SKIPPED, InboundStatus.FAILED);

    private final InboundMessageRepository inbound;

    PriceTransferServiceCheck(InboundMessageRepository inbound) {
        this.inbound = inbound;
    }

    /**
     * Услуга, которую называет часть передачи {@code cid}. Пусто, только если исход передачи уже записан, а GUID
     * услуги в части не читается.
     *
     * @throws OneCFieldException если части передачи называют разные услуги или в части нет читаемого GUID услуги
     */
    Optional<UUID> serviceOf(UUID cid, ObjectNode part) {
        if (inbound.existsByCorrelationIdAndStatusIn(cid, RECORDED_OUTCOME)) {
            return TransferServiceGuid.readableIn(part);
        }
        return Optional.of(sameAsBufferedParts(cid, TransferServiceGuid.requiredIn(part)));
    }

    private UUID sameAsBufferedParts(UUID cid, UUID service) {
        boolean mixed = inbound.findBufferedTransferServices(cid).stream()
                .anyMatch(named -> TransferServiceGuid.parse(named).filter(service::equals).isEmpty());
        if (mixed) {
            throw new OneCFieldException("части передачи %s называют разные услуги".formatted(cid));
        }
        return service;
    }
}
