package ru.projectx.datareference.service.onec;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.PriceListFinalizationRepository;
import tools.jackson.databind.node.ObjectNode;

/**
 * Блокировка строки услуги частью передачи прайса до конца транзакции вызывающего, прежде чем читать остальные части.
 * Пока строка заблокирована, проверка зависших передач той же услуги не меняет части у разбора за спиной.
 */
@Component
public class PriceTransferServiceLock {

    private final PriceTransferServiceCheck check;
    private final InboundMessageRepository inbound;
    private final PriceListFinalizationRepository finalizations;

    public PriceTransferServiceLock(InboundMessageRepository inbound, PriceListFinalizationRepository finalizations) {
        this.check = new PriceTransferServiceCheck(inbound);
        this.inbound = inbound;
        this.finalizations = finalizations;
    }

    /**
     * Строка блокируется только по услуге, которую передача называет целиком.
     *
     * @throws OneCFieldException если части передачи называют разные услуги или в части нет читаемого GUID услуги
     */
    void lockServiceOf(UUID cid, ObjectNode part) {
        check.serviceOf(cid, part).ifPresent(finalizations::lockByService);
    }

    /**
     * Блокирует строку услуги передачи {@code cid} при её отказе. Услуга передачи — та, что названа в её частях в
     * буфере: по ней же блокирует проверка зависших передач, даже если отклоняемая часть называет другую. Частей в
     * буфере нет — услуга из самой части; читаемого GUID нет нигде — блокировать нечего.
     */
    void lockTransferService(UUID cid, ObjectNode part) {
        inbound.findBufferedTransferService(cid).flatMap(TransferServiceGuid::parse)
                .or(() -> TransferServiceGuid.readableIn(part))
                .ifPresent(finalizations::lockByService);
    }
}
