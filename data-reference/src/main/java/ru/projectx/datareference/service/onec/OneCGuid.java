package ru.projectx.datareference.service.onec;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * GUID объекта 1С:УСК — только в канонической форме: 32 шестнадцатеричные цифры ASCII группами
 * 8-4-4-4-12 через дефис, всего 36 символов, оба регистра. Это грамматика {@code UUID.toString()}
 * из Javadoc Java SE 21; в примерах сообщений 1С:УСК GUID записаны именно так.
 *
 * <p>Почему не просто {@code UUID.fromString}: в JDK 21 он мягче своего Javadoc. Строку не в форме
 * 8-4-4-4-12 он читает запасным путём через {@code Long.parseLong} и молча превращает
 * {@code 1-2-3-4-5} в {@code 00000001-0002-0003-0004-000000000005}, {@code 0-0-0-0-0} — в нулевой
 * GUID, а строку в 36 символов со сдвинутыми дефисами — в другой GUID. Сообщение 1С с таким
 * значением легло бы под чужим идентификатором. Поэтому форма проверяется до разбора, а
 * {@code UUID.fromString} получает только ту строку, которую читает однозначно.
 *
 * <p>Здесь только форма. Что значит нулевой GUID и каким исключением отказать, решает вызывающий:
 * поле сообщения ({@link OneCValues}) и {@code message-id} конверта отказывают по-разному.
 */
public final class OneCGuid {

    /** Диапазоны символов буквальные: цифры и буквы не из ASCII каноническая форма не пропускает. */
    private static final Pattern CANONICAL = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** GUID из текста в канонической форме; любая другая запись — пусто. */
    public static Optional<UUID> parse(String raw) {
        return Optional.of(raw)
                .filter(text -> CANONICAL.matcher(text).matches())
                .map(UUID::fromString);
    }

    private OneCGuid() {
    }
}
