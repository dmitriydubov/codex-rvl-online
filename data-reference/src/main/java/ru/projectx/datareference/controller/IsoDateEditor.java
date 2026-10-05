package ru.projectx.datareference.controller;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Читает дату из параметров запроса REST: только запись yyyy-mm-dd, день и месяц двумя цифрами, только
 * существующая дата и без времени.
 *
 * <p>Разбор — {@link LocalDate#parse(CharSequence)}: он читает ровно эту запись и строго проверяет календарь,
 * поэтому «2026-02-30» не становится 28 февраля. Отказ разбора превращается в {@link IllegalArgumentException}:
 * только его Spring переводит в ошибку типа параметра, и сервис отвечает 400 с именем параметра. Любое другое
 * исключение дошло бы до вызывающего как 500. Присланное значение в текст отказа не попадает.
 *
 * <p>Пустое значение означает, что параметра нет. Редактор хранит прочитанное значение, поэтому для каждого
 * аргумента запроса создаётся новый экземпляр (см. {@link DateParameters}).
 */
final class IsoDateEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) {
        if (text.isBlank()) {
            setValue(null);
            return;
        }
        try {
            setValue(LocalDate.parse(text));
        } catch (DateTimeParseException notIsoDate) {
            throw new IllegalArgumentException("дата не в записи yyyy-mm-dd", notIsoDate);
        }
    }
}
