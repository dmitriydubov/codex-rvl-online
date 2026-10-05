package ru.projectx.datareference.mapper;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import ru.projectx.datareference.service.onec.ImportResult;

/**
 * Общий фильтр потоков 1С: сообщение применяется, только если запись, на которую оно ссылается, уже
 * есть в справочнике. Так хранятся данные только наших санаториев: услуги только по известным договорам,
 * цены только по известным услугам, адреса только известных санаториев.
 *
 * <p>Иначе сообщение уходит в пропуск, и причина называет GUID ссылки. Сама по себе такая запись не
 * догрузится: сообщение, пришедшее раньше записи, на которую ссылается, находится по причине пропуска
 * и обрабатывается заново, только когда его строку сырья вручную возвращают в статус {@code NEW}.
 *
 * @param <R> запись справочника, на которую ссылается сообщение
 */
final class CatalogReference<R> {

    private final Function<UUID, Optional<R>> finder;
    private final String outsideCatalog;

    /**
     * @param finder поиск записи по GUID 1С, обычно {@code findByExternalId} репозитория
     * @param outsideCatalog начало причины пропуска, к нему дописывается GUID ссылки
     */
    CatalogReference(Function<UUID, Optional<R>> finder, String outsideCatalog) {
        this.finder = finder;
        this.outsideCatalog = outsideCatalog;
    }

    /** Выполняет шаг разбора для записи с GUID 1С {@code referenceId} или пропускает сообщение. */
    ImportResult applyIfKnown(UUID referenceId, Function<R, ImportResult> step) {
        return finder.apply(referenceId)
                .map(step)
                .orElseGet(() -> ImportResult.skipped(outsideCatalog + referenceId));
    }
}
