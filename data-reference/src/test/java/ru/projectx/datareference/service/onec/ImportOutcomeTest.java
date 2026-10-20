package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.inbound.AwaitedParent;
import ru.projectx.datareference.entity.inbound.ParentKind;
import ru.projectx.datareference.mapper.ImportResult;

class ImportOutcomeTest {

    private static final AwaitedParent CONTRACT =
            new AwaitedParent(ParentKind.LPU_CONTRACT, UUID.fromString("5f14a8d2-c3e7-4b90-a1d6-9e2b7c4f0a83"));

    @Test
    void summaries_nameTheOutcomeAndItsReason() {
        assertThat(ImportOutcome.applied().summary()).isEqualTo("применено");
        assertThat(ImportOutcome.skipped("не санаторий").summary()).isEqualTo("пропущено: не санаторий");
        assertThat(ImportOutcome.awaitingParent("ждёт родителя: X").summary()).isEqualTo("ждёт родителя: X");
        assertThat(ImportOutcome.rejected("счётчики не сошлись").summary()).isEqualTo("отклонено: счётчики не сошлись");
    }

    @Test
    void onlyRejection_needsAttention() {
        assertThat(ImportOutcome.applied().needsAttention()).isFalse();
        assertThat(ImportOutcome.skipped("не санаторий").needsAttention()).isFalse();
        assertThat(ImportOutcome.awaitingParent("ждёт родителя: X").needsAttention()).isFalse();
        assertThat(ImportOutcome.rejected("счётчики не сошлись").needsAttention()).isTrue();
    }

    @Test
    void outcomeOfApplying_carriesTheMeaningOfTheMapperResult() {
        assertThat(ImportOutcome.of(ImportResult.applied())).isEqualTo(ImportOutcome.applied());
        assertThat(ImportOutcome.of(ImportResult.skipped("не наш"))).isEqualTo(ImportOutcome.skipped("не наш"));
        assertThat(ImportOutcome.of(ImportResult.awaitingParent(CONTRACT)))
                .isEqualTo(ImportOutcome.awaitingParent("ждёт родителя: договор ЛПУ " + CONTRACT.id()));
    }

    @Test
    void outcomesWithoutReason_areRejected() {
        assertThatNullPointerException().isThrownBy(() -> ImportOutcome.skipped(null)).withMessage("reason");
        assertThatNullPointerException().isThrownBy(() -> ImportOutcome.awaitingParent(null)).withMessage("reason");
        assertThatNullPointerException().isThrownBy(() -> ImportOutcome.rejected(null)).withMessage("reason");
    }
}
