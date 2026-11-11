package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.payment.InvoiceLink;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.service.issuance.IssuanceService;
import ru.projectx.contracts.service.payment.invoice.InvoiceRecording;
import ru.projectx.contracts.service.payment.order.PaymentOrderCreation;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.FixedClockConfig;
import ru.projectx.contracts.support.Tasks;

@PaymentRaceTest
class ContractStopRaceIT {

    private static final InvoiceLink LINK =
            new InvoiceLink("invoice-late", ScriptedOrdering.PAYMENT_URL + "invoice-late");

    @Autowired
    private PaymentRaceStand stand;

    @Autowired
    private ScriptedOrdering ordering;

    @Autowired
    private PaymentOrderCreation creation;

    @Autowired
    private InvoiceRecording invoices;

    @Autowired
    private IssuanceService issuance;

    @Autowired
    private RequestStatusPublication statuses;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    private final UUID request = UUID.randomUUID();
    private UUID contractId;

    @BeforeEach
    void contractWhoseCheckoutIsInFlight() {
        ordering.reset();
        stand.awaitLeadership();
        contractId = stand.acceptedContractWithOrder(request);
        creation.createDue();
        assertThat(stand.orderingEvent(contractId, "fixed", FixedClockConfig.NOW)).isEmpty();
        assertThat(invoices.start(contractId)).isPresent();
        assertThat(stand.invoice(contractId).get("state")).isEqualTo("CHECKOUT");
    }

    @AfterEach
    void removeRecords() {
        stand.remove(request);
    }

    @Test
    void exclusionThatSawTheInvoiceBeforeTheCheckoutReplyCommitted_defersTheCancellationAndKeepsTheLink()
            throws Exception {
        new RowWaits(transaction, jdbc).commitTheFirstOnceTheSecondWaitsForItsRows(
                () -> invoices.issued(contractId, LINK), this::excludeTheSpouseAfterWatching);

        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("ISSUED");
        assertThat(invoice.get("invoice_id")).isEqualTo("invoice-late");
        assertThat(invoice.get("payment_url")).isEqualTo(ScriptedOrdering.PAYMENT_URL + "invoice-late");
        assertThat(invoice.get("deferred_cancel_reason")).isEqualTo("OTHER");
        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("INVOICE_ISSUED");
        assertThat(contract.get("cancel_reason")).isNull();
        assertThat(stand.order(contractId).get("state")).isEqualTo("CREATED");
        assertThat(stand.contractsOf(request)).isEqualTo(1);
    }

    private void excludeTheSpouseAfterWatching() {
        ContractTask withoutTheSpouse = new ContractTask(2, request, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY,
                Tasks.FROM, Tasks.TO, List.of(Tasks.insured("0.00")));
        transaction.executeWithoutResult(status -> {
            IssuanceSnapshot before = statuses.watch(request);
            statuses.afterTask(before, request, issuance.apply(withoutTheSpouse, FixedClockConfig.NOW),
                    FixedClockConfig.NOW);
        });
    }
}
