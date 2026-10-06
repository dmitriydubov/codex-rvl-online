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
 * Передача прайса — это прайс одной услуги, и GUID этой услуги стоит в каждой её части. Часть, которая называет не
 * ту услугу, что части передачи в буфере, или не называет её читаемым GUID, — ошибка части: к какой услуге применить
 * цены, тогда не узнать.
 *
 * <p>Проверка читает только GUID услуги частей, без самих частей, поэтому может идти раньше блокировки строки услуги.
 * Когда исход передачи уже записан, поздняя часть разделяет его, и её услуга не проверяется.
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
