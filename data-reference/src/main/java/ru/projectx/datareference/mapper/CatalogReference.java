package ru.projectx.datareference.mapper;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.ParentKind;

/**
 * Сообщение 1С применяется, только если запись, на которую оно ссылается, уже есть в справочнике: так хранятся данные
 * только наших санаториев. Ссылка на запись, признанную не нашей, пропускается, а на неизвестную ждёт её, потому что
 * 1С присылает справочники многими очередями сразу и в произвольном порядке.
 */
final class CatalogReference<R> {

    private final Function<UUID, Optional<R>> finder;
    private final ParentKind kind;
    private final ForeignReferences foreign;
    private final String outsideCatalog;

    /** @param outsideCatalog начало причины пропуска, к нему дописывается GUID ссылки */
    CatalogReference(Function<UUID, Optional<R>> finder, ParentKind kind, ForeignReferences foreign,
                     String outsideCatalog) {
        this.finder = finder;
        this.kind = kind;
        this.foreign = foreign;
        this.outsideCatalog = outsideCatalog;
    }

    ImportResult applyIfKnown(UUID referenceId, Function<R, ImportResult> step) {
        return applyIfKnown(referenceId, step, () -> ImportResult.skipped(outsideCatalog + referenceId));
    }

    /** То же, но запись, признанная не нашей, даёт исход {@code whenForeign}, а не пропуск с причиной фильтра. */
    ImportResult applyIfKnown(UUID referenceId, Function<R, ImportResult> step, Supplier<ImportResult> whenForeign) {
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
