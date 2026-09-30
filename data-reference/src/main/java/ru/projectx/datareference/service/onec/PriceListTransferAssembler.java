package ru.projectx.datareference.service.onec;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.PriceListMapper;
import ru.projectx.datareference.repository.InboundMessageRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Сборка передачи ТЗ-9 — единственного потока, который приходит <b>частями</b>.
 *
 * <p>Прайс по одной услуге источник присылает N сообщениями со строками и ровно одним
 * финальным, где лежат счётчики. Связаны они общим {@code cid}. Применить такое сообщение
 * поодиночке нельзя: прайс замещается целиком, и половина замещения хуже, чем ничего.
 * Поэтому части копятся в буфере, а условие сборки проверяется <b>на каждом</b>
 * сообщении — порядок доставки двух очередей не гарантирован, и финальное вполне может
 * прийти раньше последнего пакета.
 *
 * <p><b>Статусы всей передачи ведёт этот класс</b>, а не общий разбор: исход у передачи
 * один на все её сообщения, и проставить его сообщению, которое пришло последним,
 * недостаточно. Возвращаемое значение поэтому описывает <i>передачу</i> и нужно
 * вызывающему только для лога — правда о каждой строке лежит в её статусе.
 */
@Component
public class PriceListTransferAssembler {

    private static final Logger log = LoggerFactory.getLogger(PriceListTransferAssembler.class);

    private static final String CID = "cid";
    private static final String KEY_VALUE = "keyValue";
    private static final String COUNT_MESSAGES = "countMessages";
    private static final String COUNT_ROWS = "countRows";
    private static final String RECORD_SET = "recordSet";
    private static final String ROW_NUMBER = "rowNumber";

    private final InboundMessageRepository inboundMessageRepository;
    private final PriceListMapper priceListMapper;
    private final ObjectMapper json;
    private final Clock clock;

    public PriceListTransferAssembler(
            InboundMessageRepository inboundMessageRepository,
            PriceListMapper priceListMapper,
            ObjectMapper json,
            Clock clock) {

        this.inboundMessageRepository = inboundMessageRepository;
        this.priceListMapper = priceListMapper;
        this.json = json;
        this.clock = clock;
    }

    /** Принимает одно сообщение передачи и пробует собрать её целиком. */
    public ImportResult receive(InboundMessage message, ObjectNode data) {
        UUID cid = OneCValues.requiredUuid(data, CID);
        buffer(message, data, cid);
        return assembleIfComplete(cid);
    }

    /**
     * Откладывает сообщение и запоминает разметку передачи: по ней сборка находит
     * остальные её части, не разбирая тела заново.
     *
     * <p>Финальное сообщение узнаётся по наличию счётчиков, а не по их значению:
     * {@code countMessages = 0} — легальный случай, означающий «прайс по услуге
     * очищается».
     */
    private void buffer(InboundMessage message, ObjectNode data, UUID cid) {
        boolean isFinal = data.has(COUNT_MESSAGES);
        Integer batchCount = isFinal ? data.path(COUNT_MESSAGES).asInt() : null;
        int rowCount = isFinal ? data.path(COUNT_ROWS).asInt() : data.path(RECORD_SET).size();

        message.markBuffered(cid, batchCount, rowCount);
        inboundMessageRepository.save(message);
    }

    private ImportResult assembleIfComplete(UUID cid) {
        List<InboundMessage> transfer = inboundMessageRepository.findByCorrelationId(cid).stream()
                .filter(row -> row.status() == InboundStatus.BUFFERED)
                .toList();

        Optional<InboundMessage> finalMessage = transfer.stream()
                .filter(row -> row.batchCount() != null)
                .findFirst();
        if (finalMessage.isEmpty()) {
            return ImportResult.skipped("передача %s ждёт финального сообщения".formatted(cid));
        }

        List<InboundMessage> batches = transfer.stream()
                .filter(row -> row.batchCount() == null)
                .toList();

        int expectedMessages = finalMessage.get().batchCount();
        if (batches.size() < expectedMessages) {
            return ImportResult.skipped("передача %s собрана на %d из %d сообщений"
                    .formatted(cid, batches.size(), expectedMessages));
        }
        return checkCountersAndApply(cid, finalMessage.get(), batches, expectedMessages);
    }

    /**
     * Счётчики не сошлись — вся передача в ошибку, прежний прайс не трогается. Лишнее
     * или недостающее сообщение означает, что источник прислал не то, что обещал;
     * применить такое значит записать заведомо неполный прайс и никогда об этом не узнать.
     */
    private ImportResult checkCountersAndApply(
            UUID cid, InboundMessage finalMessage, List<InboundMessage> batches, int expectedMessages) {

        int expectedRows = finalMessage.rowCount();
        int actualRows = batches.stream().mapToInt(row -> row.rowCount() == null ? 0 : row.rowCount()).sum();

        if (batches.size() != expectedMessages || actualRows != expectedRows) {
            String reason = "счётчики передачи %s не сошлись: ожидалось %d сообщений / %d строк, получено %d / %d"
                    .formatted(cid, expectedMessages, expectedRows, batches.size(), actualRows);
            log.error(reason);
            failTransfer(finalMessage, batches, reason);
            return ImportResult.skipped(reason);
        }
        return applyTransfer(finalMessage, batches);
    }

    private ImportResult applyTransfer(InboundMessage finalMessage, List<InboundMessage> batches) {
        ImportResult result = priceListMapper.apply(assemble(finalMessage, batches));
        switch (result) {
            case ImportResult.Applied ignored -> completeTransfer(finalMessage, batches);
            case ImportResult.Skipped skipped -> skipTransfer(finalMessage, batches, skipped.reason());
        }
        return result;
    }

    /**
     * Строки всех пакетов в одном наборе, по возрастанию номера строки: порядок задаёт
     * источник, и восстановить его после перемешивания пакетов больше неоткуда.
     */
    private ObjectNode assemble(InboundMessage finalMessage, List<InboundMessage> batches) {
        ObjectNode assembled = json.createObjectNode();
        assembled.set(KEY_VALUE, dataOf(finalMessage).path(KEY_VALUE));

        ArrayNode recordSet = assembled.putArray(RECORD_SET);
        batches.stream()
                .flatMap(batch -> rowsOf(dataOf(batch)))
                .sorted(Comparator.comparingInt(row -> row.path(ROW_NUMBER).asInt()))
                .forEach(recordSet::add);
        return assembled;
    }

    private void completeTransfer(InboundMessage finalMessage, List<InboundMessage> batches) {
        markWholeTransfer(finalMessage, batches, message -> message.markProcessed(clock.instant()));
    }

    private void skipTransfer(InboundMessage finalMessage, List<InboundMessage> batches, String reason) {
        markWholeTransfer(finalMessage, batches, message -> message.markSkipped(reason, clock.instant()));
    }

    private void failTransfer(InboundMessage finalMessage, List<InboundMessage> batches, String reason) {
        markWholeTransfer(finalMessage, batches, message -> message.markFailed(reason));
    }

    /**
     * Исход у передачи один на все её сообщения: пометить только последнее пришедшее
     * значило бы оставить остальные висеть в буфере навсегда.
     *
     * <p>Обычный цикл, а не стрим: каждый шаг меняет состояние сущности и сохраняет её —
     * побочный эффект по определению.
     */
    private void markWholeTransfer(
            InboundMessage finalMessage, List<InboundMessage> batches, Consumer<InboundMessage> transition) {

        for (InboundMessage message : Stream.concat(Stream.of(finalMessage), batches.stream()).toList()) {
            transition.accept(message);
            inboundMessageRepository.save(message);
        }
    }

    private ObjectNode dataOf(InboundMessage message) {
        String payload = message.payload();
        if (payload == null) {
            throw new OneCFieldException("тело сообщения передачи стёрто, собрать её нечем");
        }
        return (ObjectNode) json.readTree(payload).path("data");
    }

    private static Stream<JsonNode> rowsOf(ObjectNode data) {
        return StreamSupport.stream(data.path(RECORD_SET).spliterator(), false);
    }
}
