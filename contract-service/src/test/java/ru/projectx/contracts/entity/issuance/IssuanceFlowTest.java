package ru.projectx.contracts.entity.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IssuanceFlowTest {

    private static final UUID RECORD = UUID.fromString("6f3a9d21-4b8e-4c7a-b1d5-0e2f3a4b5c6d");
    private static final IssuanceFlow<String> FLOW =
            new IssuanceFlow<>("готова", "отправлена", "ошибка", "прекращена", "запись");

    @Test
    void move_givesTheNextStatusOfARecordInTheExpectedOne() {
        assertThat(FLOW.move("отправлена", "отправлена", "выпущена", RECORD)).isEqualTo("выпущена");
    }

    @Test
    void move_comparesStatusesByValue() {
        String expected = new StringBuilder("отправ").append("лена").toString();

        assertThat(FLOW.move("отправлена", expected, "выпущена", RECORD)).isEqualTo("выпущена");
    }

    @Test
    void move_isRefusedForARecordOutsideTheExpectedStatusAndNamesTheRecord() {
        assertThatThrownBy(() -> FLOW.move("готова", "отправлена", "выпущена", RECORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("запись " + RECORD + " в статусе готова, а не отправлена");
    }
}
