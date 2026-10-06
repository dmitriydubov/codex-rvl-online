package ru.projectx.datareference.mapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Общий шаг разбора справочника 1С: найти запись по GUID 1С, создать её или применить к ней новый
 * снимок и сохранить.
 *
 * <p>Версий у сообщений 1С нет, поэтому действует последнее полученное: известная запись целиком
 * получает реквизиты из нового снимка, а не сравнивает их с сохранёнными.
 *
 * <p>Сущности справочников разные и общего предка с этими операциями у них нет. Поэтому поиск,
 * создание, применение снимка и сохранение маппер передаёт функциями, обычно ссылками на методы
 * репозитория и сущности. Фильтры потока, например «услуга только по известному договору», остаются
 * в маппере и проверяются до этого шага.
 *
 * @param <E> запись справочника
 * @param <S> разобранные реквизиты сообщения, которые запись получает целиком
 */
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

    /** Создаёт или обновляет запись с GUID 1С {@code externalId} и сохраняет её. */
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

    /** Новая запись справочника из первого полученного снимка. */
    @FunctionalInterface
    interface Creation<E, S> {

        E create(UUID externalId, S snapshot, Instant now);
    }

    /** Известная запись получает реквизиты нового снимка. */
    @FunctionalInterface
    interface Refresh<E, S> {

        void apply(E entry, S snapshot, Instant now);
    }
}
