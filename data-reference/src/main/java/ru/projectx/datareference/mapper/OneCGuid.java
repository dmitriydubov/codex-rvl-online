package ru.projectx.datareference.mapper;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Разбирает GUID объекта 1С:УСК и принимает его только в канонической записи: 32 шестнадцатеричные
 * цифры ASCII группами 8-4-4-4-12 через дефис, в любом регистре.
 *
 * <p>Так GUID записан во всех примерах сообщений 1С:УСК, и так же его описывает документация
 * {@code UUID.toString()}.
 *
 * <p>Стандартный {@code UUID.fromString} в JDK 21 мягче, чем обещает его документация. Строку не в
 * форме 8-4-4-4-12 он читает запасным путём через {@code Long.parseLong}. Так {@code 1-2-3-4-5}
 * молча становится GUID {@code 00000001-0002-0003-0004-000000000005}, {@code 0-0-0-0-0} становится
 * нулевым GUID, а строка из 36 символов со сдвинутыми дефисами становится другим GUID. Сообщение с
 * таким значением легло бы под чужим идентификатором. Поэтому сначала проверяется запись, и только
 * потом строка передаётся в {@code UUID.fromString}.
 *
 * <p>Класс знает, как 1С:УСК записывает GUID: каноническую запись и пустое значение, то есть нулевой
 * GUID. Что значит пустое значение, решает вызывающий: в поле сообщения это «ссылки нет»
 * ({@link OneCValues}), а в {@code message-id} конверта это «идентификатора нет». Каким исключением
 * отказать, тоже решает вызывающий, потому что поле сообщения и {@code message-id} конверта
 * отказывают по-разному.
 */
public final class OneCGuid {

    /** Диапазоны символов заданы буквально, поэтому цифры и буквы не из ASCII не проходят. */
    private static final Pattern CANONICAL = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Нулевой GUID, которым 1С:УСК записывает пустое значение. */
    private static final UUID EMPTY = new UUID(0L, 0L);

    /**
     * Разбирает текст в GUID. Пустой результат означает, что текст не является GUID в канонической
     * записи. Нулевой GUID разбирается как обычный, а пусто ли значение, говорит {@link #isEmpty}.
     */
    public static Optional<UUID> parse(String raw) {
        return Optional.of(raw)
                .filter(text -> CANONICAL.matcher(text).matches())
                .map(UUID::fromString);
    }

    /** Говорит, пусто ли значение: в выгрузках 1С:УСК нулевой GUID означает, что значения нет. */
    public static boolean isEmpty(UUID guid) {
        return EMPTY.equals(guid);
    }

    private OneCGuid() {
    }
}
