package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.onec.ImportResult;

/**
 * Разбор потока 1С, записи которого принадлежат другой записи справочника: договор принадлежит
 * санаторию, услуга принадлежит договору.
 *
 * <p>Владелец передаётся и при обновлении записи, потому что привязка может смениться: договор отделения,
 * например, переходит от головной организации к отделению. Наследник находит владельца и разбирает
 * реквизиты, а создание или обновление записи вместе с владельцем берёт отсюда: наследник сохраняет
 * запись только через {@link #upsertUnder}, чтобы владелец и реквизиты всегда приходили вместе.
 *
 * @param <E> запись справочника
 * @param <P> запись-владелец
 * @param <D> разобранные реквизиты записи
 */
abstract class OwnedCatalogStreamMapper<E, P, D> extends CatalogStreamMapper<E, OwnedCatalogStreamMapper.Owned<P, D>> {

    /**
     * @param stream поток 1С, сообщения которого разбирает наследник
     * @param finder поиск записи по GUID 1С, обычно {@code findByExternalId} репозитория
     * @param creation новая запись, обычно фабрика {@code create} сущности
     * @param refresh новые реквизиты и владелец известной записи, обычно {@code applySnapshot} сущности
     * @param store сохранение записи, обычно {@code save} репозитория
     * @param clock часы, по которым ставится момент создания и изменения записи
     */
    OwnedCatalogStreamMapper(OneCStream stream, Function<UUID, Optional<E>> finder, Creation<E, P, D> creation,
                             Refresh<E, P, D> refresh, Consumer<E> store, Clock clock) {
        super(stream, finder,
                (externalId, owned, now) -> creation.create(externalId, owned.owner(), owned.details(), now),
                (entry, owned, now) -> refresh.apply(entry, owned.owner(), owned.details(), now),
                store, clock);
    }

    /** Создаёт или обновляет запись с GUID 1С {@code externalId}, привязанную к владельцу {@code owner}. */
    protected final ImportResult upsertUnder(P owner, UUID externalId, D details) {
        return upsert(externalId, new Owned<>(owner, details));
    }

    /** Владелец и реквизиты записи из одного сообщения. */
    record Owned<P, D>(P owner, D details) {
    }

    /** Новая запись справочника, привязанная к владельцу. */
    @FunctionalInterface
    interface Creation<E, P, D> {

        E create(UUID externalId, P owner, D details, Instant now);
    }

    /** Известная запись получает новые реквизиты и владельца. */
    @FunctionalInterface
    interface Refresh<E, P, D> {

        void apply(E entry, P owner, D details, Instant now);
    }
}
