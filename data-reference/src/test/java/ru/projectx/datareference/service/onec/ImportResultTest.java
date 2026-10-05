package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Защищает исход разбора сообщения 1С: применено или пропущено с причиной.
 *
 * <p>По исходу строке сырья проставляется статус, а планировщик пишет его в журнал. Пропуск без причины
 * запрещён: без неё «справочник пуст, потому что данных нет» и «справочник пуст, потому что фильтр задан
 * неверно» выглядят в базе одинаково.
 */
class ImportResultTest {

    @Test
    void applied_isTheApplyOutcome() {
        assertThat(ImportResult.applied()).isInstanceOf(ImportResult.Applied.class);
    }

    @Test
    void skipped_keepsItsReason() {
        ImportResult result = ImportResult.skipped("не санаторий");

        assertThat(result).isInstanceOf(ImportResult.Skipped.class);
        assertThat(((ImportResult.Skipped) result).reason()).isEqualTo("не санаторий");
    }

    @Test
    void skippedWithoutReason_isRejected() {
        assertThatThrownBy(() -> ImportResult.skipped(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("reason");
    }

    /** Исходы сравниваются по смыслу: это и проверяют тесты разбора, сравнивая исход с ожидаемым. */
    @Test
    void outcomes_areEqualByMeaning() {
        assertThat(ImportResult.applied()).isEqualTo(ImportResult.applied());
        assertThat(ImportResult.skipped("причина")).isEqualTo(ImportResult.skipped("причина"));
        assertThat(ImportResult.skipped("причина")).isNotEqualTo(ImportResult.skipped("другая"));
        assertThat(ImportResult.applied()).isNotEqualTo(ImportResult.skipped("причина"));
    }
}
