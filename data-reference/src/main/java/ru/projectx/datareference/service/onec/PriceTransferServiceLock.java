package ru.projectx.datareference.service.onec;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.PriceListFinalizationRepository;
import tools.jackson.databind.node.ObjectNode;

/**
 * Какую строку услуги блокирует часть передачи прайса, прежде чем читать остальные части. Пока строка заблокирована,
 * проверка зависших передач той же услуги не меняет части у разбора за спиной.
 *
 * <p>Передача — это прайс одной услуги, и GUID этой услуги стоит в каждой её части. Часть, которая называет не ту
 * услугу, что части передачи в буфере, или не называет её читаемым GUID, — ошибка части: к какой услуге применить
 * цены, тогда не узнать. Это проверяется раньше блокировки и по одному полю частей, без самих частей, поэтому строка
 * блокируется только по услуге, которую передача называет целиком. Когда исход передачи уже записан, поздняя часть
 * разделяет его, и её услуга не проверяется.
 *
 * <p>Методы работают в транзакции вызывающего; строка услуги остаётся заблокированной до её конца.
 */
@Component
public class PriceTransferServiceLock {

    private static final String KEY_VALUE = "keyValue";
    private static final Set<InboundStatus> RECORDED_OUTCOME =
            EnumSet.of(InboundStatus.PROCESSED, InboundStatus.SKIPPED, InboundStatus.FAILED);

    private final InboundMessageRepository inbound;
    private final PriceListFinalizationRepository finalizations;

    public PriceTransferServiceLock(InboundMessageRepository inbound, PriceListFinalizationRepository finalizations) {
        this.inbound = inbound;
        this.finalizations = finalizations;
    }

    /**
     * Проверяет услугу пришедшей части и блокирует её строку.
     *
     * @throws OneCFieldException если части передачи называют разные услуги или в части нет читаемого GUID услуги
     */
    void lockServiceOf(UUID cid, ObjectNode part) {
        serviceOf(cid, part).ifPresent(finalizations::lockByService);
    }

    /**
     * Блокирует строку услуги передачи {@code cid} при её отказе. Услуга передачи — та, что названа в её частях в
     * буфере: по ней же блокирует проверка зависших передач, даже если отклоняемая часть называет другую. Частей в
     * буфере нет — услуга из самой части; читаемого GUID нет нигде — блокировать нечего.
     */
    void lockTransferService(UUID cid, ObjectNode part) {
        inbound.findBufferedTransferService(cid).flatMap(OneCGuid::parse)
                .or(() -> readableServiceOf(part))
                .ifPresent(finalizations::lockByService);
    }

    /**
     * Услуга, которую называет часть передачи {@code cid}. Пусто, только если исход передачи уже записан, а GUID
     * услуги в части не читается.
     *
     * @throws OneCFieldException если части передачи называют разные услуги или в части нет читаемого GUID услуги
     */
    Optional<UUID> serviceOf(UUID cid, ObjectNode part) {
        if (inbound.existsByCorrelationIdAndStatusIn(cid, RECORDED_OUTCOME)) {
            return readableServiceOf(part);
        }
        return Optional.of(sameAsBufferedParts(cid, OneCValues.requiredUuid(part, KEY_VALUE)));
    }

    private UUID sameAsBufferedParts(UUID cid, UUID service) {
        boolean mixed = inbound.findBufferedTransferServices(cid).stream()
                .anyMatch(named -> !names(named, service));
        if (mixed) {
            throw new OneCFieldException("части передачи %s называют разные услуги".formatted(cid));
        }
        return service;
    }

    private static Optional<UUID> readableServiceOf(ObjectNode part) {
        return OneCValues.text(part, KEY_VALUE).flatMap(OneCGuid::parse);
    }

    private static boolean names(String named, UUID service) {
        return Optional.ofNullable(named).flatMap(OneCGuid::parse).filter(service::equals).isPresent();
    }
}
