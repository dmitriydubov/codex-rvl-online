package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.entity.inbound.ParentKind;
import ru.projectx.datareference.mapper.value.OneCValues;
import tools.jackson.databind.node.ObjectNode;

public abstract class OwnedCatalogStreamMapper<E, P, D>
        extends CatalogStreamMapper<E, OwnedCatalogStreamMapper.Owned<P, D>> {

    private static final String RECORD_ID = "id";

    private final ParentKind kind;
    private final CatalogReferences references;

    protected OwnedCatalogStreamMapper(OneCStream stream, Function<UUID, Optional<E>> finder,
                                       Creation<E, P, D> creation, Refresh<E, P, D> refresh, Consumer<E> store,
                                       Clock clock, ParentKind kind, CatalogReferences references) {
        super(stream, finder,
                (externalId, owned, now) -> creation.create(externalId, owned.owner(), owned.details(), now),
                (entry, owned, now) -> refresh.apply(entry, owned.owner(), owned.details(), now),
                store, clock);
        this.kind = kind;
        this.references = references;
    }

    @Override
    public final ImportResult apply(ObjectNode data) {
        return references.rememberIfSkipped(decide(data), kind, data);
    }

    @Override
    public final Optional<UUID> recordOf(ObjectNode data) {
        return OneCValues.uuid(data, RECORD_ID);
    }

    protected abstract ImportResult decide(ObjectNode data);

    protected final ImportResult upsertUnder(P owner, UUID externalId, D details) {
        return upsert(externalId, new Owned<>(owner, details));
    }

    record Owned<P, D>(P owner, D details) {
    }

    @FunctionalInterface
    public interface Creation<E, P, D> {

        E create(UUID externalId, P owner, D details, Instant now);
    }

    @FunctionalInterface
    public interface Refresh<E, P, D> {

        void apply(E entry, P owner, D details, Instant now);
    }
}
