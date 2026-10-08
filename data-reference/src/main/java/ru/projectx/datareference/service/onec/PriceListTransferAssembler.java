package ru.projectx.datareference.service.onec;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.PriceListMapper;
import ru.projectx.datareference.service.InboundMessageService;
import tools.jackson.databind.node.ObjectNode;

/**
 * Обработчик прайс-листов, которые 1С присылает частями: пакетами строк и финальным сообщением со счётчиками.
 * Сборка проверяется после каждой части, потому что две очереди не гарантируют порядок. Прайс замещается только
 * передачей целиком, а часть с собственной ошибкой отклоняет всю передачу после отката.
 */
@Component
public class PriceListTransferAssembler implements StreamHandler {

    private final InboundMessageService inboundMessageService;
    private final PriceTransferServiceLock serviceLock;
    private final PriceListPartCheck partCheck;
    private final PriceTransferPartsInAdvance partsInAdvance;
    private final PriceTransferConclusion conclusion;

    public PriceListTransferAssembler(
            InboundMessageService inboundMessageService,
            PriceListMapper priceListMapper,
            PriceTransferServiceLock serviceLock,
            PriceTransferPartsInAdvance partsInAdvance,
            PriceTransferConclusion conclusion) {

        this.inboundMessageService = inboundMessageService;
        this.serviceLock = serviceLock;
        this.partCheck = new PriceListPartCheck(priceListMapper);
        this.partsInAdvance = partsInAdvance;
        this.conclusion = conclusion;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.PRICE_LISTS;
    }

    /**
     * Тела частей разбираются до первого запроса транзакции, чтобы долгий разбор не держал блокировку строки услуги.
     * Пока строка заблокирована, проверка зависших передач не может решить за эту услугу посреди разбора.
     *
     * @throws OneCFieldException если часть нарушает контракт; передачу отклоняют после отката
     */
    @Override
    public ImportOutcome handle(InboundMessage message, ObjectNode data) {
        PriceListTransferMarkup markup = PriceListTransferMarkup.of(data);
        ParsedTransferParts parsed = partsInAdvance.parse(message, markup, data);
        serviceLock.lockServiceOf(markup.cid(), data);
        markup.applyTo(message);
        inboundMessageService.saveTransferPart(message);
        List<InboundMessage> parts = inboundMessageService.transferParts(markup.cid());
        partCheck.check(message.receipt().messageId(), markup, data, parts);
        return conclusion.conclude(PriceListTransfer.of(markup.cid(), parts), parsed);
    }
}
