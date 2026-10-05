package ru.projectx.datareference.service.onec;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.PriceListMapper;
import ru.projectx.datareference.service.InboundMessageService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Сборка прайс-листа по услуге, который источник присылает частями (поток прайс-листов по услугам санаториев).
 *
 * <p>Прайс по одной услуге приходит несколькими пакетами строк и одним финальным сообщением со счётчиками, все
 * с общим идентификатором передачи. Применить такое сообщение в одиночку нельзя: прайс замещается целиком, и
 * половина замещения хуже, чем ничего. Поэтому каждое сообщение откладывается в буфер, и после каждого же
 * проверяется, не собралась ли передача. Порядок доставки двух очередей не гарантирован, и финальное сообщение
 * вполне может прийти раньше последнего пакета.
 *
 * <p><b>Исход записывается всей передаче сразу</b>, а не одному сообщению: исход у передачи один, и сообщение,
 * которому его не проставили, навсегда осталось бы в буфере. Возвращаемое значение поэтому описывает передачу и
 * нужно вызывающему только для журнала. Готова ли передача и какой у неё исход, решает {@link PriceListTransfer};
 * здесь — чтение и запись сообщений и применение прайса. Сообщения передачи читает и записывает
 * {@link InboundMessageService}: там же каждый записанный статус попадает в счётчик переходов.
 */
@Component
public class PriceListTransferAssembler implements StreamHandler {

    private static final Logger log = LoggerFactory.getLogger(PriceListTransferAssembler.class);

    private static final String DATA = "data";
    private static final String KEY_VALUE = "keyValue";
    private static final String RECORD_SET = "recordSet";
    private static final String ROW_NUMBER = "rowNumber";

    private final InboundMessageService inboundMessageService;
    private final PriceListMapper priceListMapper;
    private final ObjectMapper json;
    private final Clock clock;

    public PriceListTransferAssembler(
            InboundMessageService inboundMessageService,
            PriceListMapper priceListMapper,
            ObjectMapper json,
            Clock clock) {

        this.inboundMessageService = inboundMessageService;
        this.priceListMapper = priceListMapper;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.PRICE_LISTS;
    }

    /**
     * Откладывает сообщение передачи в буфер и применяет прайс, если передача собралась целиком.
     *
     * <p>Разметка передачи запоминается в самом сообщении: по ней сборка находит остальные части, не разбирая
     * тела заново.
     */
    @Override
    public ImportResult handle(InboundMessage message, ObjectNode data) {
        PriceListTransferMarkup markup = PriceListTransferMarkup.of(data);
        markup.applyTo(message);
        inboundMessageService.saveTransferPart(message);
        return conclude(PriceListTransfer.of(markup.cid(), inboundMessageService.transferParts(markup.cid())));
    }

    private ImportResult conclude(PriceListTransfer transfer) {
        return switch (transfer) {
            case PriceListTransfer.Pending pending -> ImportResult.skipped(pending.reason());
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

    private ImportResult apply(PriceListTransfer.Assembled transfer) {
        ImportResult result = priceListMapper.apply(rowsOfWholeTransfer(transfer));
        saveAll(switch (result) {
            case ImportResult.Applied ignored -> transfer.markProcessed(clock.instant());
            case ImportResult.Skipped skipped -> transfer.markSkipped(skipped.reason(), clock.instant());
        });
        return result;
    }

    /**
     * Строки всех пакетов одним набором, по возрастанию номера строки. Порядок задаёт источник, и после того как
     * пакеты перемешались в доставке, восстановить его больше неоткуда.
     */
    private ObjectNode rowsOfWholeTransfer(PriceListTransfer.Assembled transfer) {
        ObjectNode assembled = json.createObjectNode();
        assembled.set(KEY_VALUE, dataOf(transfer.finalMessage()).path(KEY_VALUE));

        ArrayNode recordSet = assembled.putArray(RECORD_SET);
        transfer.batches().stream()
                .flatMap(batch -> rowsOf(dataOf(batch)))
                .sorted(Comparator.comparingInt(row -> row.path(ROW_NUMBER).asInt()))
                .forEach(recordSet::add);
        return assembled;
    }

    /** Обычный цикл, а не стрим: каждый шаг пишет в базу. */
    private void saveAll(List<InboundMessage> messages) {
        for (InboundMessage message : messages) {
            inboundMessageService.saveTransferPart(message);
        }
    }

    private ObjectNode dataOf(InboundMessage message) {
        String payload = message.payload();
        if (payload == null) {
            throw new OneCFieldException("тело сообщения передачи стёрто, собрать её нечем");
        }
        return (ObjectNode) json.readTree(payload).path(DATA);
    }

    private static Stream<JsonNode> rowsOf(ObjectNode data) {
        return StreamSupport.stream(data.path(RECORD_SET).spliterator(), false);
    }
}
