package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Защищает правило, по которому REST читает UUID из пути и параметров запроса: принимается только
 * каноническая запись 8-4-4-4-12 в любом регистре. Стандартное чтение UUID в JDK превращает
 * «1-2-3-4-5» в существующий GUID, и тогда сервис отдал бы чужую запись вместо отказа.
 */
class CanonicalUuidEditorTest {

    private static final UUID CONTRACT = UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea");

    private final CanonicalUuidEditor editor = new CanonicalUuidEditor();

    @ParameterizedTest
    @ValueSource(strings = {
            "77ff17b5-e659-4720-b57f-e8a19c00fcea",
            "77FF17B5-E659-4720-B57F-E8A19C00FCEA",
            "77ff17b5-E659-4720-b57f-E8A19C00FCEA"})
    void canonicalRecordInAnyCase_isTheSameUuid(String text) {
        editor.setAsText(text);

        assertThat(editor.getValue()).isEqualTo(CONTRACT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1-2-3-4-5",
            "0-0-0-0-0",
            "000000001-002-0003-0004-000000000005",
            " 77ff17b5-e659-4720-b57f-e8a19c00fcea",
            "77ff17b5-e659-4720-b57f-e8a19c00fcea ",
            "not-a-uuid"})
    void nonCanonicalRecord_isRejected(String text) {
        assertThatIllegalArgumentException().isThrownBy(() -> editor.setAsText(text));
    }

    /** Текст отказа может попасть в журнал, поэтому присланное значение в нём не повторяется. */
    @Test
    void rejection_doesNotRepeatTheValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> editor.setAsText("1-2-3-4-5"))
                .withMessageNotContaining("1-2-3-4-5");
    }

    /**
     * Пустое значение означает «параметра нет», а не «параметр нечитаем». Так его понимает и стандартное
     * чтение, и тогда обработчик отвечает 400 с текстом про обязательный параметр.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankValue_isNoValue(String text) {
        editor.setValue(CONTRACT);

        editor.setAsText(text);

        assertThat(editor.getValue()).isNull();
    }
}
