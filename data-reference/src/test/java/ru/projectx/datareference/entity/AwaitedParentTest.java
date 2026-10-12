package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AwaitedParentTest {

    private static final UUID PARENT = UUID.fromString("7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d01");

    @ParameterizedTest
    @CsvSource({"CONTRAGENT,контрагент", "LPU_CONTRACT,договор ЛПУ", "CONTRAGENT_SERVICE,услуга"})
    void reasons_nameTheKindAndTheGuidOfTheParent(ParentKind kind, String title) {
        AwaitedParent parent = new AwaitedParent(kind, PARENT);

        assertThat(parent.waitingReason()).isEqualTo("ждёт родителя: " + title + " " + PARENT);
        assertThat(parent.expiredReason()).isEqualTo("срок ожидания родителя истёк: " + title + " " + PARENT);
    }

    @Test
    void parentWithoutKindOrGuid_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new AwaitedParent(null, PARENT)).withMessage("kind");
        assertThatNullPointerException()
                .isThrownBy(() -> new AwaitedParent(ParentKind.CONTRAGENT, null))
                .withMessage("id");
    }
}
