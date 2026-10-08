package ru.projectx.datareference.controller;

import java.beans.PropertyEditorSupport;
import ru.projectx.datareference.service.CanonicalGuids;

/**
 * Читает UUID из запроса REST только в канонической записи 8-4-4-4-12, как GUID в сообщениях 1С:УСК: стандартное
 * чтение через {@code UUID.fromString} принимает и «1-2-3-4-5». Неканоническая запись даёт 400 без присланного значения
 * в тексте, а пустая означает, что параметра нет. Редактор хранит значение, поэтому создаётся на каждый аргумент.
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
