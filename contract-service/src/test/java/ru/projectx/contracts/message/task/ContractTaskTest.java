package ru.projectx.contracts.message.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.support.Tasks;

class ContractTaskTest {

    @Test
    void insured_isTheParticipantWithoutRelativeFlagWhereverItStands() {
        ContractTask task = Tasks.task(Tasks.spouse("34000.00"), Tasks.insured("0.00"), Tasks.child("0.00"));

        assertThat(task.insured().id()).isEqualTo(Tasks.INSURED);
    }

    @Test
    void insured_isMissingWhenEveryParticipantIsRelative() {
        ContractTask task = Tasks.task(Tasks.spouse("34000.00"), Tasks.child("0.00"));

        assertThatThrownBy(task::insured).isInstanceOf(IllegalStateException.class)
                .hasMessage("в составе поездки нет застрахованного лица");
    }
}
