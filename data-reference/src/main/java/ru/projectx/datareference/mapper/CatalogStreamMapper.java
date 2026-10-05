package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.StreamMapper;

/**
 * Разбор потока 1С, который ведёт справочник по GUID 1С: сообщение создаёт запись или заменяет её
 * реквизиты последним полученным снимком.
 *
 * <p>Наследник решает, принимать ли сообщение и как из него получается снимок, а создание или обновление
 * записи с моментом по часам сервиса берёт отсюда. Поиск, создание, применение снимка и сохранение
 * передаются функциями, как в {@link SnapshotUpsert}.
 *
 * @param <E> запись справочника
 * @param <S> разобранные реквизиты сообщения
 */
abstract class CatalogStreamMapper<E, S> implements StreamMapper {

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

    /** Создаёт или обновляет запись с GUID 1С {@code externalId} и сохраняет её. */
    protected final ImportResult upsert(UUID externalId, S snapshot) {
        return upsert.apply(externalId, snapshot, clock.instant());
    }
}
