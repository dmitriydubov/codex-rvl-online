package ru.projectx.contracts.config.broker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class QueueNamesTest {

    @Test
    void distinctNames_areAccepted() {
        assertThatCode(() -> QueueNames.requireDistinct("a", "b", "c")).doesNotThrowAnyException();
    }

    @Test
    void repeatedName_isRejectedWithItsName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QueueNames.requireDistinct("a", "b", "a"))
                .withMessage("имя очереди задано двум свойствам: a");
    }

    @Test
    void missingNames_areIgnored() {
        assertThatCode(() -> QueueNames.requireDistinct(null, "a", null)).doesNotThrowAnyException();
    }
}
