package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Защищает правило, по которому REST читает дату из параметра запроса: только запись yyyy-mm-dd с годом в четыре
 * цифры без знака, днём и месяцем в две цифры и только существующая дата. Нестрогое чтение приняло бы «2026-02-30»
 * как 28 февраля или дату со временем, и стоимость посчиталась бы не за тот период, о котором спрашивали. Год со
 * знаком или из пяти цифр дал бы период на тысячи лет.
 */
class IsoDateEditorTest {

    private final IsoDateEditor editor = new IsoDateEditor();

    @Test
    void isoDate_isRead() {
        editor.setAsText("2026-04-10");

        assertThat(editor.getValue()).isEqualTo(LocalDate.of(2026, 4, 10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.04.2026", "2026-4-10", "2026-02-30", "2026-04-31", "2026-04-10T00:00:00",
            " 2026-04-10", "2026-04-10+03:00", "+10000-01-01", "-0001-01-01", "+2026-04-10", "+999999999-12-31",
            "20260-04-10", "026-04-10"})
    void otherRecordOrMissingDate_isRejected_asIllegalArgument(String text) {
        assertThatIllegalArgumentException().isThrownBy(() -> editor.setAsText(text));
    }

    /** Текст отказа может попасть в журнал, поэтому присланное значение в нём не повторяется. */
    @Test
    void rejection_doesNotRepeatTheValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> editor.setAsText("10.04.2026"))
                .withMessageNotContaining("10.04.2026");
    }

    /** Пустое значение означает «параметра нет», и обработчик отвечает 400 с текстом про обязательный параметр. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankValue_isNoValue(String text) {
        editor.setValue(LocalDate.of(2026, 4, 10));

        editor.setAsText(text);

        assertThat(editor.getValue()).isNull();
    }
}
