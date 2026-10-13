package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.ContractViolationException;

class InboxEventHandlersTest {

    private final InboxEventHandler upserted = mock();
    private final InboxEventHandler cancelled = mock();
    private final InboxEventHandler issued = mock();

    @BeforeEach
    void handlersAreNamed() {
        given(upserted.eventType()).willReturn("ContractTaskUpserted");
        given(upserted.ordering()).willReturn(InboxOrdering.BY_REQUEST_VERSION);
        given(cancelled.eventType()).willReturn("ContractTaskCancelled");
        given(cancelled.ordering()).willReturn(InboxOrdering.BY_REQUEST_VERSION);
        given(issued.eventType()).willReturn("LmPolicyIssued");
        given(issued.ordering()).willReturn(InboxOrdering.NONE);
    }

    @Test
    void handler_isFoundByItsEventType() {
        InboxEventHandlers handlers = new InboxEventHandlers(List.of(upserted, cancelled, issued));

        assertThat(handlers.forEventType("ContractTaskCancelled")).isSameAs(cancelled);
        assertThat(handlers.forEventType("ContractTaskUpserted")).isSameAs(upserted);
        assertThat(handlers.forEventType("LmPolicyIssued")).isSameAs(issued);
    }

    @Test
    void eventTypesOrderedByRequestVersion_areThoseOfTheHandlersThatDeclareIt() {
        InboxEventHandlers handlers = new InboxEventHandlers(List.of(upserted, cancelled, issued));

        assertThat(handlers.orderedByRequestVersion()).containsExactlyInAnyOrder("ContractTaskUpserted",
                "ContractTaskCancelled");
    }

    @Test
    void unknownEventType_isContractViolationNamingTheType() {
        InboxEventHandlers handlers = new InboxEventHandlers(List.of(upserted));

        assertThatThrownBy(() -> handlers.forEventType("ContractTaskRenamed"))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("неизвестный тип события ContractTaskRenamed");
    }
}
