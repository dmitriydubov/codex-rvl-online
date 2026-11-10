package ru.projectx.contracts.service.payment.order;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.support.PaymentOrders;

class DocumentFixesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:10:00Z");

    private final DocumentFixMessages messages = mock();
    private final OutboxJournal journal = mock();
    private final DocumentFixes fixes = new DocumentFixes(messages, journal);

    @Test
    void record_journalsTheDocumentConfirmationOfTheOrderAtTheGivenMoment() {
        Contract contract = PaymentOrders.acceptedContract();
        PaymentOrder order = PaymentOrders.created();
        OutboxDraft draft = mock();
        given(messages.draft(contract, order, NOW)).willReturn(draft);

        fixes.record(contract, order, NOW);

        verify(journal).record(draft, NOW);
        verifyNoMoreInteractions(journal);
    }
}
