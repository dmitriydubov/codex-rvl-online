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
 * Сборка прайс-листа по услуге, который источник присылает частями: пакетами строк и финальным сообщением со
 * счётчиками, связанными общим идентификатором передачи.
 *
 * <p>Каждая часть откладывается в буфер, и после каждой проверяется, не собралась ли передача: порядок двух очередей
 * не гарантирован. Прайс замещается только передачей целиком, и исход записывается сразу всем её частям. Цены
 * меняет передача, финальное сообщение которой принято не раньше финала уже применённой ({@link PriceTransferOrder}).
 * Часть с собственной ошибкой ({@link PriceTransferServiceCheck}, {@link PriceListPartCheck}) прерывает разбор: её и
 * остальные части передачи отклоняет {@link PriceListTransferRejection} после отката. Исход передачи по её частям
 * решает и записывает {@link PriceTransferConclusion}.
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
     * Откладывает часть передачи в буфер с её разметкой и применяет прайс, если передача собралась. Тела частей
     * собирающейся передачи разбираются первыми, до первого запроса этой транзакции. Затем проверяется, что часть
     * называет ту же услугу, что и вся передача; блокируется строка этой услуги, и проверка зависших передач решает
     * за неё только до или после этого разбора.
     *
     * @throws OneCFieldException если часть нарушает контракт: передачу отклоняют после отката
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
