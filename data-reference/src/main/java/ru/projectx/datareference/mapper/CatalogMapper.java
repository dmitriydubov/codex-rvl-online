package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCValues;
import ru.projectx.datareference.service.onec.StreamMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбор потока 1С, который приносит справочник целиком и без фильтра: каждое сообщение создаёт
 * запись с GUID 1С из поля {@code id} или заменяет её реквизиты последним полученным снимком.
 *
 * <p>Наследник отвечает только за то, как из сообщения получается снимок его справочника.
 * Потоки с фильтром, например услуги только по известным договорам, сюда не подходят: им нужно
 * решить, принимать ли сообщение, ещё до поиска записи.
 *
 * @param <E> запись справочника
 * @param <S> разобранные реквизиты сообщения
 */
abstract class CatalogMapper<E, S> implements StreamMapper {

    private static final String EXTERNAL_ID = "id";

    private final OneCStream stream;
    private final SnapshotUpsert<E, S> upsert;
    private final Clock clock;

    /**
     * @param finder поиск записи по GUID 1С, обычно {@code findByExternalId} репозитория
     * @param creation новая запись из первого снимка
     * @param refresh применение нового снимка к известной записи
     * @param store сохранение записи, обычно {@code save} репозитория
     */
    CatalogMapper(OneCStream stream, Function<UUID, Optional<E>> finder, SnapshotUpsert.Creation<E, S> creation,
                  SnapshotUpsert.Refresh<E, S> refresh, Consumer<E> store, Clock clock) {
        this.stream = stream;
        this.upsert = new SnapshotUpsert<>(finder, creation, refresh, store);
        this.clock = clock;
    }

    @Override
    public final OneCStream stream() {
        return stream;
    }

    /** GUID записи читается раньше снимка: без него сообщение нарушает контракт, что бы ни было в остальных полях. */
    @Override
    public final ImportResult apply(ObjectNode data) {
        return upsert.apply(OneCValues.requiredUuid(data, EXTERNAL_ID), snapshotOf(data), clock.instant());
    }

    /**
     * Снимок справочника из поля {@code data} сообщения.
     *
     * @throws ru.projectx.datareference.exception.OneCFieldException если сообщение нарушает контракт
     */
    protected abstract S snapshotOf(ObjectNode data);
}
