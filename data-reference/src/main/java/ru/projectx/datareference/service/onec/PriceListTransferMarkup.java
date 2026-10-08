package ru.projectx.datareference.service.onec;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.OneCValues;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разметка части передачи прайса: передача, обещанное число пакетов (есть только у финального сообщения) и число
 * строк в пакете или, у финального, во всей передаче. Она запоминается в сообщении при откладывании в буфер, чтобы
 * сборка находила части и сверяла счётчики, не разбирая тела заново.
 */
record PriceListTransferMarkup(UUID cid, @Nullable Integer batchCount, int rowCount) {

    private static final String CID = "cid";
    private static final String COUNT_MESSAGES = "countMessages";
    private static final String COUNT_ROWS = "countRows";
    private static final String RECORD_SET = "recordSet";

    /**
     * Финальное сообщение узнаётся по наличию счётчиков, а не по значению: ноль пакетов означает очистку прайса.
     *
     * @throws OneCFieldException если нет идентификатора передачи или счётчик дробный либо не помещается в {@code int}
     */
    static PriceListTransferMarkup of(ObjectNode data) {
        UUID cid = cidOf(data);
        if (data.has(COUNT_MESSAGES)) {
            return new PriceListTransferMarkup(cid, counter(data, COUNT_MESSAGES), counter(data, COUNT_ROWS));
        }
        return new PriceListTransferMarkup(cid, null, data.path(RECORD_SET).size());
    }

    /**
     * По идентификатору передачу находят и тогда, когда остальная разметка сообщения не читается.
     *
     * @throws OneCFieldException если в сообщении нет идентификатора передачи
     */
    static UUID cidOf(ObjectNode data) {
        return OneCValues.requiredUuid(data, CID);
    }

    /** Дробь и число за пределами {@code int} отклоняются с именем поля, прочее читается через {@code asInt}. */
    private static int counter(ObjectNode data, String field) {
        return OneCValues.integer(data, field).orElseGet(() -> data.path(field).asInt());
    }

    /** Откладывает сообщение в буфер с этой разметкой. */
    void applyTo(InboundMessage message) {
        message.markBuffered(cid, batchCount, rowCount);
    }
}
