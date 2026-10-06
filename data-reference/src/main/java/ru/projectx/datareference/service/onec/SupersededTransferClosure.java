package ru.projectx.datareference.service.onec;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;

/**
 * Закрывает пропуском зависшую передачу прайса, которая уже не может стать последней по своей услуге.
 *
 * <p>Передача не может стать последней, если по её услуге применена передача, финал которой принят позже всех её
 * частей, или если её собственный финал старше. Всем её частям в буфере ставится пропуск с причиной, и о ней больше
 * не предупреждают. Части, пришедшие потом, пропускаются при разборе. Передача, которую обогнать ещё можно, остаётся
 * в буфере для ручного разбора.
 */
@Component
public class SupersededTransferClosure {

    private static final Logger log = LoggerFactory.getLogger(SupersededTransferClosure.class);

    private final InboundMessageRepository repository;
    private final PriceTransferOrder order;
    private final InboundMessageService inboundMessageService;
    private final Clock clock;

    public SupersededTransferClosure(InboundMessageRepository repository, PriceTransferOrder order,
            InboundMessageService inboundMessageService, Clock clock) {

        this.repository = repository;
        this.order = order;
        this.inboundMessageService = inboundMessageService;
        this.clock = clock;
    }

    /**
     * Закрывает передачу {@code cid}, если по её услуге применена передача, которую она уже не обгонит. Своя
     * транзакция: строка услуги блокируется до чтения частей. Разбор части с той же услугой блокирует её тоже до
     * чтения частей, поэтому видит либо передачу до закрытия, либо уже закрытую.
     *
     * @return закрыта ли передача
     */
    @Transactional
    public boolean closeIfSuperseded(UUID cid) {
        return repository.findBufferedTransferService(cid)
                .flatMap(OneCGuid::parse)
                .flatMap(order::lockApplied)
                .map(applied -> closeIfOlder(cid, applied))
                .orElse(false);
    }

    private boolean closeIfOlder(UUID cid, AppliedPriceTransfer applied) {
        List<InboundMessage> buffered = inboundMessageService.transferParts(cid).stream()
                .filter(part -> part.outcome().status() == InboundStatus.BUFFERED)
                .toList();
        if (!applied.supersedes(buffered)) {
            return false;
        }
        skipAll(buffered, applied.reason());
        log.info("передача прайса {} закрыта пропуском: {}", cid, applied.reason());
        return true;
    }

    /** Обычный цикл, а не стрим: каждый шаг пишет в базу. */
    private void skipAll(List<InboundMessage> parts, String reason) {
        Instant now = clock.instant();
        for (InboundMessage part : parts) {
            part.markSkipped(reason, now);
            inboundMessageService.saveTransferPart(part);
        }
    }
}
