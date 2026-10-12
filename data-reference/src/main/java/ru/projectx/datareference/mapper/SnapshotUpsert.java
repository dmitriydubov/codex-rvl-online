package ru.projectx.datareference.mapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

final class SnapshotUpsert<E, S> {

    private final Function<UUID, Optional<E>> finder;
    private final Creation<E, S> creation;
    private final Refresh<E, S> refresh;
    private final Consumer<E> store;

    SnapshotUpsert(Function<UUID, Optional<E>> finder, Creation<E, S> creation, Refresh<E, S> refresh,
                   Consumer<E> store) {
        this.finder = finder;
        this.creation = creation;
        this.refresh = refresh;
        this.store = store;
    }

    ImportResult apply(UUID externalId, S snapshot, Instant now) {
        E entry = finder.apply(externalId)
                .map(known -> refreshed(known, snapshot, now))
                .orElseGet(() -> creation.create(externalId, snapshot, now));
        store.accept(entry);
        return ImportResult.applied();
    }

    private E refreshed(E known, S snapshot, Instant now) {
        refresh.apply(known, snapshot, now);
        return known;
    }

    @FunctionalInterface
    interface Creation<E, S> {

        E create(UUID externalId, S snapshot, Instant now);
    }

    @FunctionalInterface
    interface Refresh<E, S> {

        void apply(E entry, S snapshot, Instant now);
    }
}
