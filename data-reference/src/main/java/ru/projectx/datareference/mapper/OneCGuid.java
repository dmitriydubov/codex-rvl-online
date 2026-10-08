package ru.projectx.datareference.mapper;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * GUID объекта 1С:УСК только в канонической записи 8-4-4-4-12. Запись проверяется до {@code UUID.fromString}, потому
 * что в JDK 21 он молча читает {@code 1-2-3-4-5} как другой GUID, и сообщение легло бы под чужим идентификатором.
 * Что значит пустой (нулевой) GUID и каким исключением отказать, решает вызывающий.
 */
public final class OneCGuid {

    /** Диапазоны символов заданы буквально, поэтому цифры и буквы не из ASCII не проходят. */
    private static final Pattern CANONICAL = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Нулевой GUID, которым 1С:УСК записывает пустое значение. */
    private static final UUID EMPTY = new UUID(0L, 0L);

    /** Нулевой GUID разбирается как обычный, а пусто ли значение, говорит {@link #isEmpty}. */
    public static Optional<UUID> parse(String raw) {
        return Optional.of(raw)
                .filter(text -> CANONICAL.matcher(text).matches())
                .map(UUID::fromString);
    }

    public static boolean isEmpty(UUID guid) {
        return EMPTY.equals(guid);
    }

    private OneCGuid() {
    }
}
