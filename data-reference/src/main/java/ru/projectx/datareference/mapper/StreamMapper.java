package ru.projectx.datareference.mapper;

import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбор сообщений одного потока 1С: фильтры потока и upsert в таблицы справочника.
 *
 * <p>По реализации на поток. Общего у них ровно столько, сколько в этом интерфейсе:
 * диспетчер выбирает реализацию по {@link OneCStream} и не знает ни одного правила
 * ни одного из потоков.
 *
 * <p>Реализация не трогает строку сырья и не открывает транзакцию: и то, и другое —
 * дело вызывающего, потому что изменение справочника и новый статус сырья обязаны
 * попасть в одну транзакцию.
 */
public interface StreamMapper {

    /** Поток, сообщения которого разбирает эта реализация. */
    OneCStream stream();

    /**
     * @param data поле {@code data} сообщения 1С — как его прислал источник
     * @throws OneCFieldException если сообщение не соответствует контракту; это
     *         постоянная ошибка, повторять её бессмысленно
     */
    ImportResult apply(ObjectNode data);
}
