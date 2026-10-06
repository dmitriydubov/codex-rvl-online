package ru.projectx.datareference.controller;

import java.beans.PropertyEditorSupport;
import ru.projectx.datareference.service.CanonicalGuids;

/**
 * Читает UUID из пути и параметров запроса REST по тому же правилу, по которому сервис читает GUID
 * из сообщений 1С:УСК: только каноническая запись из 32 шестнадцатеричных цифр группами 8-4-4-4-12,
 * в любом регистре.
 *
 * <p>Стандартное чтение UUID в Spring обрезает пробелы и передаёт строку в {@code UUID.fromString},
 * а тот в JDK 21 принимает и неполные записи. Тогда запрос с «1-2-3-4-5» нашёл бы запись с GUID
 * 00000001-0002-0003-0004-000000000005 и вернул бы её вместо отказа. Этот редактор отказывает
 * неканонической записи, и сервис отвечает 400 с именем параметра. Присланное значение в текст
 * отказа не попадает.
 *
 * <p>Пустое значение по-прежнему означает, что параметра нет, как и при стандартном чтении.
 *
 * <p>Редактор хранит прочитанное значение, поэтому для каждого аргумента запроса создаётся новый
 * экземпляр (см. {@link UuidParameters}).
 */
final class CanonicalUuidEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) {
        if (text.isBlank()) {
            setValue(null);
            return;
        }
        setValue(CanonicalGuids.parse(text)
                .orElseThrow(() -> new IllegalArgumentException("UUID не в канонической записи 8-4-4-4-12")));
    }
}
