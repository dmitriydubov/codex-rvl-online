package ru.projectx.datareference.service.onec;

import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.node.ObjectNode;

/**
 * Обработка сообщений одного потока 1С: разбор и запись исхода в строку сырья.
 *
 * <p>Большинство потоков обрабатываются одинаково: маппер потока разбирает сообщение, и исход ложится в ту же
 * строку сырья. Так работает {@link MappedStreamHandler}. Поток, которому нужен другой путь, получает свою
 * реализацию, например прайс-листы, которые приходят частями и собираются {@link PriceListTransferAssembler}.
 * Разбор сообщения выбирает обработчик по потоку и ни одного из этих путей не знает.
 *
 * <p>Реализация не открывает транзакцию: изменения справочника и статус сырья попадают в транзакцию
 * вызывающего и фиксируются вместе.
 */
public interface StreamHandler {

    /** Поток, сообщения которого обрабатывает эта реализация. */
    OneCStream stream();

    /**
     * Обрабатывает одно сообщение потока и записывает исход.
     *
     * @param message строка сырья, которой записывается исход
     * @param data    поле {@code data} из конверта сообщения
     * @return исход разбора: он нужен вызывающему для журнала, правда о каждой строке лежит в её статусе
     * @throws OneCFieldException если сообщение не соответствует контракту; это постоянная ошибка,
     *         повторять её бессмысленно
     */
    ImportOutcome handle(InboundMessage message, ObjectNode data);
}
