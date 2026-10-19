package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.mapper.value.OneCValues;
import tools.jackson.databind.node.ObjectNode;

abstract class UnfilteredStreamMapper<E, S> extends CatalogStreamMapper<E, S> {

    private static final String EXTERNAL_ID = "id";

    UnfilteredStreamMapper(OneCStream stream, Function<UUID, Optional<E>> finder, SnapshotUpsert.Creation<E, S> creation,
                           SnapshotUpsert.Refresh<E, S> refresh, Consumer<E> store, Clock clock) {
        super(stream, finder, creation, refresh, store, clock);
    }

    @Override
    public final ImportResult apply(ObjectNode data) {
        return upsert(OneCValues.requiredUuid(data, EXTERNAL_ID), snapshotOf(data));
    }

    protected abstract S snapshotOf(ObjectNode data);
}
