package ru.projectx.datareference.service.onec;

import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.node.ObjectNode;

/**
 * Обработка сообщений одного потока 1С: разбор и запись исхода в строку сырья. Поток с особым путём, как прайс-листы,
 * получает свою реализацию вместо ветки в общем разборе. Реализация не открывает транзакцию: изменения справочника
 * и статус сырья фиксируются вместе в транзакции вызывающего.
 */
public interface StreamHandler {

    OneCStream stream();

    /**
     * @return исход разбора для журнала; правда о каждой строке сырья лежит в её статусе
     * @throws OneCFieldException если сообщение не соответствует контракту; повторять такую ошибку бессмысленно
     */
    ImportOutcome handle(InboundMessage message, ObjectNode data);
}
