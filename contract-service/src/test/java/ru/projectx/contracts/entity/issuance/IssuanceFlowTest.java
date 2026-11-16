package ru.projectx.contracts.entity.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IssuanceFlowTest {

    private static final UUID RECORD = UUID.fromString("6f3a9d21-4b8e-4c7a-b1d5-0e2f3a4b5c6d");
    private static final IssuanceFlow<SampleRecordStatus> FLOW = new IssuanceFlow<>(SampleRecordStatus.READY,
            SampleRecordStatus.SENT, SampleRecordStatus.FAILED, SampleRecordStatus.STOPPED, "запись");

    @Test
    void move_givesTheNextStatusOfARecordInTheExpectedOne() {
        assertThat(FLOW.move(SampleRecordStatus.SENT, SampleRecordStatus.SENT, SampleRecordStatus.ISSUED, RECORD))
                .isEqualTo(SampleRecordStatus.ISSUED);
    }

    @Test
    void move_isRefusedForARecordOutsideTheExpectedStatusAndNamesTheRecord() {
        assertThatThrownBy(() -> FLOW.move(SampleRecordStatus.READY, SampleRecordStatus.SENT, SampleRecordStatus.ISSUED,
                RECORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("запись " + RECORD + " в статусе READY, а не SENT");
    }
}
