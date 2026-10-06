package ru.projectx.datareference.service.onec;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.OneCValues;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разметка сообщения передачи прайс-листа: к какой передаче оно относится, финальное ли оно и сколько в нём строк.
 *
 * <p>Разметка запоминается в самом сообщении, когда оно откладывается в буфер. По ней сборка находит остальные
 * части передачи и сверяет счётчики, не разбирая тела заново.
 *
 * @param cid        идентификатор передачи, общий для всех её сообщений
 * @param batchCount сколько пакетов строк обещает источник; есть только у финального сообщения
 * @param rowCount   у пакета — сколько строк в нём пришло, у финального — сколько строк во всей передаче
 */
record PriceListTransferMarkup(UUID cid, @Nullable Integer batchCount, int rowCount) {

    private static final String CID = "cid";
    private static final String COUNT_MESSAGES = "countMessages";
    private static final String COUNT_ROWS = "countRows";
    private static final String RECORD_SET = "recordSet";

    /**
     * Читает разметку из поля {@code data} сообщения.
     *
     * <p>Финальное сообщение узнаётся по наличию счётчиков, а не по их значению: ноль пакетов — легальный случай,
     * он означает, что прайс по услуге очищается.
     *
     * @throws OneCFieldException если в сообщении нет идентификатора передачи
     */
    static PriceListTransferMarkup of(ObjectNode data) {
        UUID cid = OneCValues.requiredUuid(data, CID);
        if (data.has(COUNT_MESSAGES)) {
            return new PriceListTransferMarkup(cid, data.path(COUNT_MESSAGES).asInt(), data.path(COUNT_ROWS).asInt());
        }
        return new PriceListTransferMarkup(cid, null, data.path(RECORD_SET).size());
    }

    /** Откладывает сообщение в буфер с этой разметкой. */
    void applyTo(InboundMessage message) {
        message.markBuffered(cid, batchCount, rowCount);
    }
}
