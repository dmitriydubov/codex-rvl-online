package ru.projectx.contracts.repository.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceTerm;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.Invoices;

@IntegrationTest
class OrderInvoiceRepositoryTest {

    private static final Instant NOW = PaymentRows.NOW;
    private static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");
    private static final Cancellation DEFERRED = new Cancellation(CancelReason.INSURED_REFUSED, "Стенд: комментарий",
            Instant.parse("2026-02-12T11:30:00Z"));

    @Autowired
    private OrderInvoiceRepository invoices;

    @Autowired
    private PaymentOrderRepository orders;

    @Autowired
    private ContractRepository contracts;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    private PaymentRows rows;

    @BeforeEach
    void emptyTablesBefore() {
        rows = new PaymentRows(contracts, orders, invoices, transaction);
        rows.deleteAll();
    }

    @AfterEach
    void emptyTablesAfter() {
        rows.deleteAll();
    }

    @Test
    void newInvoice_isStoredAwaitingTheDocumentWithoutTermLinkPaymentOrDeferredCancellation() {
        PaymentOrder order = rows.created(NOW, "o-1");
        rows.invoice(order, NOW, invoice -> { });

        assertThat(invoices.findByContractId(order.view().contractId())).map(OrderInvoice::view)
                .contains(new InvoiceView(order.view().id(), order.view().contractId(),
                        InvoiceState.AWAITING_DOCUMENT, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()));
        assertThat(jdbc.sql("select state::text from payment_invoices").query(String.class).single())
                .isEqualTo("AWAITING_DOCUMENT");
    }

    @Test
    void issuedInvoice_roundTripsItsTermLinkRepeatsAndTheDeferredCancellationWithNativeEnums() {
        PaymentOrder order = rows.created(NOW, "o-1");
        rows.invoice(order, NOW, invoice -> {
            invoice.repeatFix(NOW);
            invoice.repeatFix(NOW);
            invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, NOW);
            invoice.start(Invoices.DEADLINE, Invoices.CHECKOUT_STARTED);
            invoice.issue(Invoices.LINK, NOW);
            invoice.deferCancellation(DEFERRED, NOW);
        });

        InvoiceView found = invoices.findByContractId(order.view().contractId()).orElseThrow().view();

        assertThat(found).isEqualTo(new InvoiceView(order.view().id(), order.view().contractId(), InvoiceState.ISSUED,
                2, Optional.of(new InvoiceTerm(Invoices.CHECKOUT_STARTED, Invoices.DEADLINE)),
                Optional.of(Invoices.LINK), Optional.empty(), Optional.empty(), Optional.of(DEFERRED)));
        assertThat(jdbc.sql("select state::text || '/' || deferred_cancel_reason::text from payment_invoices")
                .query(String.class).single()).isEqualTo("ISSUED/INSURED_REFUSED");
    }

    @Test
    void paidAndRejectedInvoices_keepTheMomentOfPaymentAndTheRefusalCode() {
        PaymentOrder paid = rows.created(NOW, "o-1");
        PaymentOrder rejected = rows.created(NOW, "o-2");
        rows.invoice(paid, NOW, invoice -> {
            invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, NOW);
            invoice.start(Invoices.DEADLINE, NOW);
            invoice.pay(PAID_AT, NOW);
        });
        rows.invoice(rejected, NOW, invoice -> {
            invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, NOW);
            invoice.start(Invoices.DEADLINE, NOW);
            invoice.reject("CheckoutValidatorService.valid", NOW);
        });

        assertThat(invoices.findByContractId(paid.view().contractId()).orElseThrow().view())
                .returns(InvoiceState.PAID, InvoiceView::state)
                .returns(Optional.of(PAID_AT), InvoiceView::paidAt);
        assertThat(invoices.findByContractId(rejected.view().contractId()).orElseThrow().view())
                .returns(InvoiceState.REJECTED, InvoiceView::state)
                .returns(Optional.of("CheckoutValidatorService.valid"), InvoiceView::rejectionCode);
    }

    @ParameterizedTest
    @EnumSource(InvoiceState.class)
    void everyInvoiceState_isAValueOfTheDatabaseType(InvoiceState state) {
        PaymentOrder order = rows.created(NOW, "o-1");
        rows.invoice(order, NOW, invoice -> ReflectionTestUtils.setField(invoice, "state", state));

        assertThat(invoices.findByContractId(order.view().contractId()).orElseThrow().view().state()).isEqualTo(state);
        assertThat(jdbc.sql("select state::text from payment_invoices").query(String.class).single())
                .isEqualTo(state.name());
    }

    @Test
    void invoices_areFoundByTheContractsTheyBelongTo() {
        PaymentOrder first = rows.created(NOW, "o-1");
        PaymentOrder second = rows.created(NOW, "o-2");
        PaymentOrder third = rows.created(NOW, "o-3");
        rows.invoice(first, NOW, invoice -> { });
        rows.invoice(second, NOW, invoice -> { });
        rows.invoice(third, NOW, invoice -> { });

        assertThat(invoices.findByContractIdIn(List.of(first.view().contractId(), third.view().contractId(),
                UUID.fromString("00000000-0000-4000-8000-000000000001"))))
                .extracting(invoice -> invoice.view().orderId())
                .containsExactlyInAnyOrder(first.view().id(), third.view().id());
        assertThat(invoices.findByContractIdIn(List.of())).isEmpty();
        assertThat(invoices.findByContractId(UUID.fromString("00000000-0000-4000-8000-000000000001"))).isEmpty();
    }

    @Test
    void secondInvoiceOfTheSameOrder_isRefusedByTheDatabase() {
        PaymentOrder order = rows.created(NOW, "o-1");
        rows.invoice(order, NOW, invoice -> { });

        assertThatThrownBy(() -> rows.invoice(order, NOW, invoice -> { }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_payment_invoices_");
    }

    @Test
    void invoicesCreatedBefore_areCountedAmongTheStatesOfOrdersInTheGivenState() {
        Set<InvoiceState> notInvoiced =
                EnumSet.of(InvoiceState.AWAITING_DOCUMENT, InvoiceState.DOCUMENT_UNCONFIRMED, InvoiceState.READY);
        rows.invoice(rows.created(NOW, "o-1"), NOW.minusSeconds(10), invoice -> { });
        rows.invoice(rows.created(NOW, "o-2"), NOW.minusSeconds(10),
                invoice -> invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, NOW));
        rows.invoice(rows.created(NOW, "o-3"), NOW, invoice -> { });
        rows.invoice(rows.created(NOW, "o-4"), NOW.minusSeconds(10), invoice -> {
            invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, NOW);
            invoice.start(Invoices.DEADLINE, NOW);
        });
        rows.invoice(rows.order(NOW, PaymentRows.BOUNDARY, order -> {
            order.markRequested(NOW);
            order.recordOrder("o-5", NOW);
            order.withdraw(NOW);
        }), NOW.minusSeconds(10), invoice -> { });

        assertThat(invoices.countCreatedBefore(PaymentOrderState.CREATED, notInvoiced, NOW)).isEqualTo(2);
        assertThat(invoices.countCreatedBefore(PaymentOrderState.CREATED, notInvoiced, NOW.plusMillis(1))).isEqualTo(3);
        assertThat(invoices.countCreatedBefore(PaymentOrderState.CREATED, notInvoiced, NOW.minusSeconds(10))).isZero();
        assertThat(invoices.countCreatedBefore(PaymentOrderState.CANCEL_PENDING, notInvoiced, NOW)).isEqualTo(1);
    }

    @Test
    void invoicesDueBefore_areCountedByTheirPaymentDeadlineAmongTheStatesOfOrdersInTheGivenState() {
        Set<InvoiceState> awaitingOutcome = EnumSet.of(InvoiceState.ISSUED, InvoiceState.PAYMENT_ERROR);
        rows.invoice(rows.created(NOW, "o-1"), NOW, OrderInvoiceRepositoryTest::issued);
        rows.invoice(rows.created(NOW, "o-2"), NOW, invoice -> {
            issued(invoice);
            invoice.pass(InvoiceTransition.PAYMENT_FAILED, NOW);
        });
        rows.invoice(rows.created(NOW, "o-3"), NOW, invoice -> {
            issued(invoice);
            invoice.pay(PAID_AT, NOW);
        });
        rows.invoice(rows.created(NOW, "o-4"), NOW, invoice -> { });

        assertThat(invoices.countDueBefore(PaymentOrderState.CREATED, awaitingOutcome, Invoices.DEADLINE.plusMillis(1)))
                .isEqualTo(2);
        assertThat(invoices.countDueBefore(PaymentOrderState.CREATED, awaitingOutcome, Invoices.DEADLINE)).isZero();
        assertThat(invoices.countDueBefore(PaymentOrderState.CANCELLED, awaitingOutcome,
                Invoices.DEADLINE.plusMillis(1))).isZero();
    }

    private static void issued(OrderInvoice invoice) {
        invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, NOW);
        invoice.start(Invoices.DEADLINE, NOW);
        invoice.issue(Invoices.LINK, NOW);
    }
}
