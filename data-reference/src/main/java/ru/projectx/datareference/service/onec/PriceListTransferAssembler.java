package ru.projectx.datareference.service.onec;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Часть с собственной ошибкой ({@link PriceListPartCheck}) прерывает разбор: её и остальные части передачи отклоняет
 * {@link PriceListTransferRejection} после отката.
 */
@Component
public class PriceListTransferAssembler implements StreamHandler {

    private static final Logger log = LoggerFactory.getLogger(PriceListTransferAssembler.class);

    private final InboundMessageService inboundMessageService;
    private final PriceTransferOrder order;
    private final PriceListReplacement replacement;
    private final PriceListPartCheck partCheck;
    private final Clock clock;

    public PriceListTransferAssembler(
            InboundMessageService inboundMessageService,
            PriceListMapper priceListMapper,
            PriceTransferOrder order,
            PriceListReplacement replacement,
            Clock clock) {

        this.inboundMessageService = inboundMessageService;
        this.order = order;
        this.replacement = replacement;
        this.partCheck = new PriceListPartCheck(priceListMapper);
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.PRICE_LISTS;
    }

    /**
     * Откладывает часть передачи в буфер с её разметкой и применяет прайс, если передача собралась. Строка услуги
     * блокируется первой: проверка зависших передач решает за ту же услугу только до или после этого разбора.
     *
     * @throws OneCFieldException если часть нарушает контракт: передачу отклоняют после отката
     */
    @Override
    public ImportResult handle(InboundMessage message, ObjectNode data) {
        order.lockServiceOf(data);
        PriceListTransferMarkup markup = PriceListTransferMarkup.of(data);
        markup.applyTo(message);
        inboundMessageService.saveTransferPart(message);
        List<InboundMessage> parts = inboundMessageService.transferParts(markup.cid());
        partCheck.check(message.receipt().messageId(), markup, data, parts);
        return conclude(PriceListTransfer.of(markup.cid(), parts));
    }

    private ImportResult conclude(PriceListTransfer transfer) {
        return switch (transfer) {
            case PriceListTransfer.Pending pending -> ImportResult.skipped(pending.reason());
            case PriceListTransfer.Rejected rejected -> rejectLate(rejected);
            case PriceListTransfer.Closed closed -> skipLate(closed);
            case PriceListTransfer.Mismatched mismatched -> fail(mismatched);
            case PriceListTransfer.Assembled assembled -> apply(assembled);
        };
    }

    /** Прежний прайс по услуге не трогается: передача, не совпавшая со своими счётчиками, заведомо неполна. */
    private ImportResult fail(PriceListTransfer.Mismatched transfer) {
        log.error(transfer.reason());
        saveAll(transfer.fail());
        return ImportResult.skipped(transfer.reason());
    }

    /** Отказ передачи уже записан и о нём уже сообщено; опоздавшая часть просто разделяет его. */
    private ImportResult rejectLate(PriceListTransfer.Rejected transfer) {
        log.warn(transfer.reason());
        saveAll(transfer.fail());
        return ImportResult.skipped(transfer.reason());
    }

    /** Пропущенная передача уже не применится; поздняя часть разделяет её пропуск. */
    private ImportResult skipLate(PriceListTransfer.Closed transfer) {
        saveAll(transfer.skip(clock.instant()));
        return ImportResult.skipped(transfer.reason());
    }

    /** Замещает прайс собранной передачей или пропускает её, если по услуге применена более новая; исход — всем частям. */
    private ImportResult apply(PriceListTransfer.Assembled transfer) {
        ImportResult result = replacement.replace(transfer);
        saveAll(transfer.markOutcome(result, clock.instant()));
        return result;
    }

    /** Обычный цикл, а не стрим: каждый шаг пишет в базу. */
    private void saveAll(List<InboundMessage> messages) {
        for (InboundMessage message : messages) {
            inboundMessageService.saveTransferPart(message);
        }
    }
}
