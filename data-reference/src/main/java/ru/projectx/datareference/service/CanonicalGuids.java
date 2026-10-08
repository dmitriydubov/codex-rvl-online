package ru.projectx.datareference.service;

import java.util.Optional;
import java.util.UUID;
import ru.projectx.datareference.mapper.OneCGuid;

/**
 * Разбирает GUID из запросов REST по тому же правилу, что и GUID в сообщениях 1С:УСК ({@link OneCGuid}).
 * Контроллеры берут правило отсюда, чтобы не зависеть от пакета разбора сообщений.
 */
public final class CanonicalGuids {

    /** Пусто, если текст не GUID в канонической записи 8-4-4-4-12. */
    public static Optional<UUID> parse(String raw) {
        return OneCGuid.parse(raw);
    }

    private CanonicalGuids() {
    }
}
