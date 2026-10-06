package ru.projectx.datareference.service.onec;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.service.InboundMessageService;

/**
 * Исход передачи прайса, к которому пришла её очередная часть: передача ждёт остальных частей, разделяет уже записанный
 * отказ или пропуск, отклоняется из-за несошедшихся счётчиков или замещает прайс. Исход записывается сразу всем частям.
 *
 * <p>Работает в транзакции сборщика передачи, под блокировкой строки услуги: исход фиксируется вместе с ценами.
 */
@Component
public class PriceTransferConclusion {

    private static final Logger log = LoggerFactory.getLogger(PriceTransferConclusion.class);

    private final InboundMessageService inboundMessageService;
    private final PriceListReplacement replacement;
    private final Clock clock;

    public PriceTransferConclusion(
            InboundMessageService inboundMessageService, PriceListReplacement replacement, Clock clock) {
        this.inboundMessageService = inboundMessageService;
        this.replacement = replacement;
        this.clock = clock;
    }

    /**
     * @param parsed тела частей, разобранные до блокировки строки услуги
     * @throws OneCFieldException если тело части собранной передачи стёрто или у строки нет номера
     */
    ImportOutcome conclude(PriceListTransfer transfer, ParsedTransferParts parsed) {
        return switch (transfer) {
            case PriceListTransfer.Pending pending -> ImportOutcome.skipped(pending.reason());
            case PriceListTransfer.Rejected rejected -> rejectLate(rejected);
            case PriceListTransfer.Closed closed -> skipLate(closed);
            case PriceListTransfer.Mismatched mismatched -> fail(mismatched);
            case PriceListTransfer.Assembled assembled -> replacement.replace(assembled, parsed, clock.instant());
        };
    }

    /** Прежний прайс по услуге не трогается: передача, не совпавшая со своими счётчиками, заведомо неполна. */
    private ImportOutcome fail(PriceListTransfer.Mismatched transfer) {
        log.error(transfer.reason());
        saveAll(transfer.fail());
        return ImportOutcome.rejected(transfer.reason());
    }

    /** Отказ передачи уже записан и о нём уже сообщено; опоздавшая часть просто разделяет его. */
    private ImportOutcome rejectLate(PriceListTransfer.Rejected transfer) {
        log.warn(transfer.reason());
        saveAll(transfer.fail());
        return ImportOutcome.rejected(transfer.reason());
    }

    /** Пропущенная передача уже не применится; поздняя часть разделяет её пропуск. */
    private ImportOutcome skipLate(PriceListTransfer.Closed transfer) {
        saveAll(transfer.skip(clock.instant()));
        return ImportOutcome.skipped(transfer.reason());
    }

    /** Обычный цикл, а не стрим: каждый шаг пишет в базу. */
    private void saveAll(List<InboundMessage> messages) {
        for (InboundMessage message : messages) {
            inboundMessageService.saveTransferPart(message);
        }
    }
}
