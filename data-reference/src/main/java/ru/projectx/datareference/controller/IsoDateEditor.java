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
 * Читает дату из запроса строго как yyyy-mm-dd: {@link LocalDate#parse(CharSequence)} принимает год со знаком и
 * длиннее четырёх цифр, а нестрогий календарь превратил бы «2026-02-30» в 28 февраля. Отказ становится
 * {@link IllegalArgumentException}: его Spring переводит в 400, а любое другое исключение дало бы 500.
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
