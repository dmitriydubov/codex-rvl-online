package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбор потока 1С без фильтра, где каждое сообщение создаёт запись или заменяет её реквизиты. Потоки с фильтром
 * сюда не подходят: им нужно решить, принимать ли сообщение, ещё до поиска записи.
 */
abstract class UnfilteredStreamMapper<E, S> extends CatalogStreamMapper<E, S> {

    private static final String EXTERNAL_ID = "id";

    UnfilteredStreamMapper(OneCStream stream, Function<UUID, Optional<E>> finder, SnapshotUpsert.Creation<E, S> creation,
                           SnapshotUpsert.Refresh<E, S> refresh, Consumer<E> store, Clock clock) {
        super(stream, finder, creation, refresh, store, clock);
    }

    /** GUID записи читается раньше снимка: без него сообщение нарушает контракт, что бы ни было в остальных полях. */
    @Override
    public final ImportResult apply(ObjectNode data) {
        return upsert(OneCValues.requiredUuid(data, EXTERNAL_ID), snapshotOf(data));
    }

    /** @throws ru.projectx.datareference.exception.OneCFieldException если сообщение нарушает контракт */
    protected abstract S snapshotOf(ObjectNode data);
}
