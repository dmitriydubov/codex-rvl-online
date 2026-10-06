package ru.projectx.datareference.service.onec;

import org.springframework.stereotype.Component;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.PriceListMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Замещение прайса услуги собранной передачей с учётом порядка передач: строки всех пакетов идут в замещение одним
 * набором, если по услуге не применена более новая передача.
 */
@Component
public class PriceListReplacement {

    private final PriceListMapper priceListMapper;
    private final PriceTransferOrder order;
    private final PriceListTransferRows rows;

    public PriceListReplacement(PriceListMapper priceListMapper, PriceTransferOrder order, ObjectMapper json) {
        this.priceListMapper = priceListMapper;
        this.order = order;
        this.rows = new PriceListTransferRows(json);
    }

    /**
     * Работает в транзакции вызывающего: строка услуги остаётся заблокированной до её конца.
     *
     * @return применено, пропущено по порядку передач или пропущено по правилам прайса
     * @throws OneCFieldException если тело части стёрто, у строки нет номера или в передаче нет GUID услуги
     */
    ImportResult replace(PriceListTransfer.Assembled transfer) {
        ObjectNode assembled = rows.of(transfer);
        return order.applyInOrder(PriceListTransferRows.serviceOf(assembled), transfer.finalMessage(),
                () -> priceListMapper.apply(assembled));
    }
}
