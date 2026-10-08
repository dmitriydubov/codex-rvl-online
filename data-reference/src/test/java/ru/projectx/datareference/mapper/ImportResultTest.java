package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.ParentKind;

/**
 * Защищает исход применения сообщения 1С. Пропуск без причины запрещён, иначе «данных нет» и «фильтр задан неверно»
 * выглядят в базе одинаково; ожидание без родителя запрещено, иначе разбор не знал бы, когда брать сообщение снова.
 */
class ImportResultTest {

    private static final AwaitedParent CONTRAGENT =
            new AwaitedParent(ParentKind.CONTRAGENT, UUID.fromString("6d0e4a7b-2c95-4f18-8b3d-e9a1c7f50d62"));

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

    @Test
    void awaitingParent_keepsTheAwaitedRecord() {
        ImportResult result = ImportResult.awaitingParent(CONTRAGENT);

        assertThat(result).isInstanceOf(ImportResult.AwaitingParent.class);
        assertThat(((ImportResult.AwaitingParent) result).parent()).isEqualTo(CONTRAGENT);
    }

    @Test
    void awaitingWithoutParent_isRejected() {
        assertThatThrownBy(() -> ImportResult.awaitingParent(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("parent");
    }

    /** Исходы сравниваются по смыслу: на этом держатся тесты разбора, сравнивающие исход с ожидаемым. */
    @Test
    void outcomes_areEqualByMeaning() {
        assertThat(ImportResult.applied()).isEqualTo(ImportResult.applied());
        assertThat(ImportResult.skipped("причина")).isEqualTo(ImportResult.skipped("причина"));
        assertThat(ImportResult.skipped("причина")).isNotEqualTo(ImportResult.skipped("другая"));
        assertThat(ImportResult.applied()).isNotEqualTo(ImportResult.skipped("причина"));
        assertThat(ImportResult.awaitingParent(CONTRAGENT)).isEqualTo(ImportResult.awaitingParent(CONTRAGENT));
    }
}
