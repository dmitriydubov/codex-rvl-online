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
 * Разбор потока 1С без фильтра: принимается каждое сообщение, и оно создаёт запись с GUID 1С из поля
 * {@code id} или заменяет её реквизиты последним полученным снимком. Так устроены справочники видов
 * обращения и диагнозов МКБ-10, а также полисы застрахованных.
 *
 * <p>Наследник отвечает только за то, как из сообщения получается снимок записи. Потоки с фильтром,
 * например услуги только по известным договорам, сюда не подходят: им нужно решить, принимать ли
 * сообщение, ещё до поиска записи.
 *
 * @param <E> запись, которую создаёт или обновляет поток
 * @param <S> разобранные реквизиты сообщения
 */
abstract class UnfilteredStreamMapper<E, S> implements StreamMapper {

    private static final String EXTERNAL_ID = "id";

    private final OneCStream stream;
    private final SnapshotUpsert<E, S> upsert;
    private final Clock clock;

    /**
     * @param stream поток 1С, сообщения которого разбирает наследник
     * @param finder поиск записи по GUID 1С, обычно {@code findByExternalId} репозитория
     * @param creation новая запись из первого снимка
     * @param refresh применение нового снимка к известной записи
     * @param store сохранение записи, обычно {@code save} репозитория
     * @param clock часы, по которым ставится момент создания и изменения записи
     */
    UnfilteredStreamMapper(OneCStream stream, Function<UUID, Optional<E>> finder, SnapshotUpsert.Creation<E, S> creation,
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
