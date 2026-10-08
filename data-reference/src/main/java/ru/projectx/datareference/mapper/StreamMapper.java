package ru.projectx.datareference.mapper;

import java.util.Optional;
import java.util.UUID;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбор сообщений одного потока 1С: фильтры потока и запись в справочник, по реализации на поток. Реализация
 * не трогает строку сырья и не открывает транзакцию: изменение справочника и новый статус сырья обязаны попасть
 * в одну транзакцию вызывающего.
 */
public interface StreamMapper {

    OneCStream stream();

    /** @throws OneCFieldException если сообщение не соответствует контракту; повторять такую ошибку бессмысленно */
    ImportResult apply(ObjectNode data);

    /**
     * GUID записи, которую меняет сообщение; нужен потокам, где сообщения ждут родителя: более новое применённое
     * сообщение закрывает ожидающие старые той же записи.
     */
    default Optional<UUID> recordOf(ObjectNode data) {
        return Optional.empty();
    }
}
