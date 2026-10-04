package ru.projectx.datareference.service.onec;

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
 * <p>Класс проверяет только запись. Нулевой GUID в канонической записи для него обычный GUID, а что
 * он значит в поле сообщения, решает {@link OneCValues}. Каким исключением отказать, решает
 * вызывающий, потому что поле сообщения и {@code message-id} конверта отказывают по-разному.
 */
public final class OneCGuid {

    /** Диапазоны символов заданы буквально, поэтому цифры и буквы не из ASCII не проходят. */
    private static final Pattern CANONICAL = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Пустой результат означает, что текст не является GUID в канонической записи. */
    public static Optional<UUID> parse(String raw) {
        return Optional.of(raw)
                .filter(text -> CANONICAL.matcher(text).matches())
                .map(UUID::fromString);
    }

    private OneCGuid() {
    }
}
