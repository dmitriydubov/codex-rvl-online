package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import ru.projectx.datareference.entity.OneCStream;

public abstract class CatalogStreamMapper<E, S> implements StreamMapper {

    private final OneCStream stream;
    private final SnapshotUpsert<E, S> upsert;
    private final Clock clock;

    protected CatalogStreamMapper(OneCStream stream, Function<UUID, Optional<E>> finder,
                                  SnapshotUpsert.Creation<E, S> creation, SnapshotUpsert.Refresh<E, S> refresh,
                                  Consumer<E> store, Clock clock) {
        this.stream = stream;
        this.upsert = new SnapshotUpsert<>(finder, creation, refresh, store);
        this.clock = clock;
    }

    @Override
    public final OneCStream stream() {
        return stream;
    }

    protected final ImportResult upsert(UUID externalId, S snapshot) {
        return upsert.apply(externalId, snapshot, clock.instant());
    }
}
