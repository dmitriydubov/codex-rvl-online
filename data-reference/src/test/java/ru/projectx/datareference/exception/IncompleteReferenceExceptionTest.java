package ru.projectx.datareference.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Отказ 422 называет ЛПУ, у которого нет договора с ценами: по этому ЛПУ данные исправляют в 1С. */
class IncompleteReferenceExceptionTest {

    private static final UUID LPU = UUID.fromString("0a9e0000-0000-4000-8000-000000000004");

    @Test
    void noActivePriceContract_hasCode_andNamesLpu() {
        IncompleteReferenceException incomplete = IncompleteReferenceException.noActivePriceContract(LPU);

        assertThat(incomplete.code()).isEqualTo("NO_ACTIVE_PRICE_CONTRACT");
        assertThat(incomplete).hasMessage("у ЛПУ " + LPU + " нет действующего договора, по которому брать цены");
        assertThat(incomplete.lpuId()).isEqualTo(LPU);
    }
}
