package ru.projectx.datareference.mapper;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import ru.projectx.datareference.entity.inbound.AwaitedParent;
import ru.projectx.datareference.entity.inbound.ParentKind;

public final class CatalogReference<R> {

    private final Function<UUID, Optional<R>> finder;
    private final ParentKind kind;
    private final ForeignReferences foreign;
    private final String outsideCatalog;

    CatalogReference(Function<UUID, Optional<R>> finder, ParentKind kind, ForeignReferences foreign,
                     String outsideCatalog) {
        this.finder = finder;
        this.kind = kind;
        this.foreign = foreign;
        this.outsideCatalog = outsideCatalog;
    }

    public ImportResult applyIfKnown(UUID referenceId, Function<R, ImportResult> step) {
        return applyIfKnown(referenceId, step, () -> ImportResult.skipped(outsideCatalog + referenceId));
    }

    public ImportResult applyIfKnown(UUID referenceId, Function<R, ImportResult> step,
                                     Supplier<ImportResult> whenForeign) {
        return finder.apply(referenceId)
                .map(step)
                .orElseGet(() -> withoutRecord(referenceId, whenForeign));
    }

    private ImportResult withoutRecord(UUID referenceId, Supplier<ImportResult> whenForeign) {
        return foreign.isForeign(kind, referenceId)
                ? whenForeign.get()
                : ImportResult.awaitingParent(new AwaitedParent(kind, referenceId));
    }
}
