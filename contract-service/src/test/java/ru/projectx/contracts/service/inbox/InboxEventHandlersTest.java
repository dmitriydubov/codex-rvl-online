package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.ContractViolationException;

class InboxEventHandlersTest {

    private final InboxEventHandler upserted = mock();
    private final InboxEventHandler cancelled = mock();

    @Test
    void handler_isFoundByItsEventType() {
        given(upserted.eventType()).willReturn("ContractTaskUpserted");
        given(cancelled.eventType()).willReturn("ContractTaskCancelled");
        InboxEventHandlers handlers = new InboxEventHandlers(List.of(upserted, cancelled));

        assertThat(handlers.forEventType("ContractTaskCancelled")).isSameAs(cancelled);
        assertThat(handlers.forEventType("ContractTaskUpserted")).isSameAs(upserted);
    }

    @Test
    void unknownEventType_isContractViolationNamingTheType() {
        given(upserted.eventType()).willReturn("ContractTaskUpserted");
        InboxEventHandlers handlers = new InboxEventHandlers(List.of(upserted));

        assertThatThrownBy(() -> handlers.forEventType("ContractTaskRenamed"))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("неизвестный тип события ContractTaskRenamed");
    }
}
