package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Разбор потока 1С, который ведёт справочник по GUID 1С. Наследник решает, принимать ли сообщение и как получить
 * из него снимок, а запись создаётся или обновляется через {@link SnapshotUpsert} по часам сервиса.
 */
abstract class CatalogStreamMapper<E, S> implements StreamMapper {

    private final OneCStream stream;
    private final SnapshotUpsert<E, S> upsert;
    private final Clock clock;

    CatalogStreamMapper(OneCStream stream, Function<UUID, Optional<E>> finder, SnapshotUpsert.Creation<E, S> creation,
                        SnapshotUpsert.Refresh<E, S> refresh, Consumer<E> store, Clock clock) {
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
