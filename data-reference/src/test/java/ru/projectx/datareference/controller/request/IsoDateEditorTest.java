package ru.projectx.datareference.controller.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void rejection_doesNotRepeatTheValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> editor.setAsText("10.04.2026"))
                .withMessageNotContaining("10.04.2026");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankValue_isNoValue(String text) {
        editor.setValue(LocalDate.of(2026, 4, 10));

        editor.setAsText(text);

        assertThat(editor.getValue()).isNull();
    }
}
