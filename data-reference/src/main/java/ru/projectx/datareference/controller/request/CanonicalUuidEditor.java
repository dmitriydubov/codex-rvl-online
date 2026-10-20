package ru.projectx.datareference.controller.request;

import java.beans.PropertyEditorSupport;
import ru.projectx.datareference.service.rest.CanonicalGuids;

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
