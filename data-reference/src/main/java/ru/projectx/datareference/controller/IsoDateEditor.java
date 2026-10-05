package ru.projectx.datareference.controller;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;

/**
 * Читает дату из параметров запроса REST: только запись yyyy-mm-dd, год четырьмя цифрами без знака, день и месяц
 * двумя цифрами, только существующая дата и без времени.
 *
 * <p>Стандартный разбор {@link LocalDate#parse(CharSequence)} для этого не годится: он принимает год со знаком и
 * больше чем из четырёх цифр, например «+10000-01-01». Поэтому запись задана здесь явно, а календарь проверяется
 * строго, и «2026-02-30» не становится 28 февраля. Отказ разбора превращается в {@link IllegalArgumentException}:
 * только его Spring переводит в ошибку типа параметра, и сервис отвечает 400 с именем параметра. Любое другое
 * исключение дошло бы до вызывающего как 500. Присланное значение в текст отказа не попадает.
 *
 * <p>Пустое значение означает, что параметра нет. Редактор хранит прочитанное значение, поэтому для каждого
 * аргумента запроса создаётся новый экземпляр (см. {@link DateParameters}).
 */
final class IsoDateEditor extends PropertyEditorSupport {

    /** Год — ровно четыре цифры: так он записан в yyyy-mm-dd, и знак перед ним не допускается. */
    private static final int YEAR_DIGITS = 4;
    private static final int MONTH_AND_DAY_DIGITS = 2;

    private static final DateTimeFormatter YYYY_MM_DD = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, YEAR_DIGITS)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, MONTH_AND_DAY_DIGITS)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, MONTH_AND_DAY_DIGITS)
            .toFormatter()
            .withChronology(IsoChronology.INSTANCE)
            .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public void setAsText(String text) {
        if (text.isBlank()) {
            setValue(null);
            return;
        }
        try {
            setValue(LocalDate.parse(text, YYYY_MM_DD));
        } catch (DateTimeParseException notIsoDate) {
            throw new IllegalArgumentException("дата не в записи yyyy-mm-dd", notIsoDate);
        }
    }
}
