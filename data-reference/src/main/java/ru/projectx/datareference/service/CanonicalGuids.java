package ru.projectx.datareference.service;

import java.util.Optional;
import java.util.UUID;
import ru.projectx.datareference.mapper.OneCGuid;

/**
 * Каноническая запись GUID для идентификаторов из запросов REST: то же правило, по которому сервис читает GUID из
 * сообщений 1С:УСК.
 *
 * <p>Правило записи одно на весь сервис и описано в {@link OneCGuid}, рядом с разбором сообщений. Контроллеры берут
 * его отсюда и от пакета разбора не зависят.
 */
public final class CanonicalGuids {

    /** Пустой результат означает, что текст не является GUID в канонической записи 8-4-4-4-12. */
    public static Optional<UUID> parse(String raw) {
        return OneCGuid.parse(raw);
    }

    private CanonicalGuids() {
    }
}
