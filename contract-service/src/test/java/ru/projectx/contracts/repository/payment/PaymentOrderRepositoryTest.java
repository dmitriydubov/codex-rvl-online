package ru.projectx.contracts.repository.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.dto.PayerEmailFormat;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.OrderRequest;
import ru.projectx.contracts.entity.payment.PayerEmail;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderKeys;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.Invoices;

@IntegrationTest
class PaymentOrderRepositoryTest {

    private static final Instant NOW = PaymentRows.NOW;
    private static final Instant NEVER = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant LONG_AFTER = Invoices.DEADLINE.plusSeconds(3600);
    private static final Set<PaymentOrderState> AWAITING_INVOICE =
            EnumSet.of(PaymentOrderState.CREATED, PaymentOrderState.REJECTED);
    private static final Set<InvoiceState> INVOICED = EnumSet.of(InvoiceState.CHECKOUT, InvoiceState.ISSUED,
            InvoiceState.PAYMENT_ERROR, InvoiceState.PAID, InvoiceState.OVERDUE);

    @Autowired
    private PaymentOrderRepository orders;

    @Autowired
    private OrderInvoiceRepository invoices;

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
    void payerAddress_isStoredWithTheOrderAndGoneOnceForgotten() {
        PaymentOrder order = rows.order(NOW, PaymentRows.BOUNDARY, opened -> { });

        assertThat(orders.findById(order.view().id()).orElseThrow().view().payer())
                .contains(new PayerEmail(PaymentRows.PAYER));
        assertThat(jdbc.sql("select payer_email from payment_orders").query(String.class).single())
                .isEqualTo(PaymentRows.PAYER);

        order.forgetPayer(NOW);
        orders.save(order);

        assertThat(orders.findById(order.view().id()).orElseThrow().view().payer()).isEmpty();
        assertThat(jdbc.sql("select count(*) from payment_orders where payer_email is null").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void longestPayerAddressTheAcceptanceAllows_isStoredInFull() {
        String domain = "@stand.example.test";
        PayerEmail longest = new PayerEmail("p".repeat(PayerEmailFormat.MAX_LENGTH - domain.length()) + domain);

        PaymentOrder order = rows.orderOf(longest, NOW, PaymentRows.BOUNDARY, opened -> { });

        assertThat(longest.address()).hasSize(254);
        assertThat(orders.findById(order.view().id()).orElseThrow().view().payer()).contains(longest);
    }

    @ParameterizedTest
    @EnumSource(PaymentOrderState.class)
    void everyOrderState_isAValueOfTheDatabaseType(PaymentOrderState state) {
        PaymentOrder order = rows.order(NOW, PaymentRows.BOUNDARY,
                opened -> ReflectionTestUtils.setField(opened, "state", state));

        assertThat(orders.findById(order.view().id()).orElseThrow().view().state()).isEqualTo(state);
        assertThat(jdbc.sql("select state::text from payment_orders").query(String.class).single())
                .isEqualTo(state.name());
    }

    @Test
    void keys_areFoundByTheOrderIdentifierOfTheOrderingService() {
        PaymentOrder first = rows.created(NOW, "o-1");
        rows.created(NOW, "o-2");

        assertThat(orders.findKeysByExternalOrderId("o-1"))
                .contains(new PaymentOrderKeys(first.view().contractId(), first.view().correlationId()));
        assertThat(orders.findKeysByExternalOrderId("o-3")).isEmpty();
    }

    @Test
    void ordersWithInvoiceInAState_areThoseOfTheOrderStateInCreationOrderWithinTheLimit() {
        PaymentOrder later = rows.created(NOW.plusSeconds(5), "o-1");
        PaymentOrder earlier = rows.created(NOW, "o-2");
        PaymentOrder awaitingDocument = rows.created(NOW.minusSeconds(5), "o-3");
        PaymentOrder withdrawn = rows.order(NOW.minusSeconds(9), PaymentRows.BOUNDARY, order -> {
            order.markRequested(OrderRequest.CREATION, NOW);
            order.recordOrder("o-4", NOW);
            order.withdraw(NOW);
        });
        rows.created(NOW.minusSeconds(20), "o-5");
        rows.invoice(later, NOW, invoice -> invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, NOW));
        rows.invoice(earlier, NOW, invoice -> invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, NOW));
        rows.invoice(awaitingDocument, NOW, invoice -> { });
        rows.invoice(withdrawn, NOW, invoice -> invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, NOW));

        assertThat(ids(orders.findWithInvoiceIn(PaymentOrderState.CREATED, InvoiceState.READY, Limit.of(10))))
                .containsExactly(id(earlier), id(later));
        assertThat(ids(orders.findWithInvoiceIn(PaymentOrderState.CREATED, InvoiceState.READY, Limit.of(1))))
                .containsExactly(id(earlier));
        assertThat(ids(orders.findWithInvoiceIn(PaymentOrderState.CREATED, InvoiceState.AWAITING_DOCUMENT,
                Limit.of(10)))).containsExactly(id(awaitingDocument));
        assertThat(ids(orders.findWithInvoiceIn(PaymentOrderState.CANCEL_PENDING, InvoiceState.READY, Limit.of(10))))
                .containsExactly(id(withdrawn));
    }

    @Test
    void ordersPastTheBoundary_areThoseWithoutAStartedInvoiceWhoseBoundaryIsStrictlyBeforeNow() {
        Instant passed = NOW.minusSeconds(1);
        PaymentOrder withoutInvoice = rows.order(NOW.minusSeconds(30), passed, PaymentOrderRepositoryTest::created);
        PaymentOrder readyInvoice = rows.order(NOW.minusSeconds(20), passed, PaymentOrderRepositoryTest::created);
        PaymentOrder rejected = rows.order(NOW.minusSeconds(10), passed, order -> order.reject("C", NOW));
        PaymentOrder invoiced = rows.order(NOW.minusSeconds(40), passed, PaymentOrderRepositoryTest::created);
        rows.order(NOW.minusSeconds(50), NOW, PaymentOrderRepositoryTest::created);
        rows.order(NOW.minusSeconds(60), passed, order -> { });
        rows.invoice(readyInvoice, NOW, invoice -> invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, NOW));
        rows.invoice(invoiced, NOW, invoice -> {
            invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, NOW);
            invoice.start(Invoices.DEADLINE, NOW);
        });

        assertThat(ids(orders.findPastBoundary(AWAITING_INVOICE, INVOICED, NOW, Limit.of(10))))
                .containsExactly(id(withoutInvoice), id(readyInvoice), id(rejected));
        assertThat(ids(orders.findPastBoundary(AWAITING_INVOICE, INVOICED, NOW, Limit.of(2))))
                .containsExactly(id(withoutInvoice), id(readyInvoice));
    }

    @Test
    void invoiceToReconcile_isOneLastTouchedStrictlyBeforeTheStaleMoment() {
        PaymentOrder order = rows.created(NOW, "o-1");
        rows.invoice(order, NOW, ready(NOW));

        assertThat(reconciled(NOW, NEVER)).isEmpty();
        assertThat(reconciled(NOW.plusMillis(1), NEVER)).containsExactly(id(order));
    }

    @Test
    void invoiceToReconcile_isAFailedPaymentWithDeadlineStrictlyBeforeTheDueMomentHoweverRecentlyTouched() {
        PaymentOrder order = rows.created(NOW, "o-1");
        rows.invoice(order, NOW, failedPayment(NOW));

        assertThat(reconciled(NOW, Invoices.DEADLINE)).isEmpty();
        assertThat(reconciled(NOW, Invoices.DEADLINE.plusMillis(1))).containsExactly(id(order));
    }

    @Test
    void failedPaymentBothStaleAndDue_comesOnceAndStalenessAloneIsEnoughForIt() {
        PaymentOrder order = rows.created(NOW, "o-1");
        rows.invoice(order, NOW, failedPayment(NOW));

        assertThat(reconciled(NOW.plusMillis(1), Invoices.DEADLINE.plusMillis(1))).containsExactly(id(order));
        assertThat(reconciled(NOW.plusMillis(1), Invoices.DEADLINE)).containsExactly(id(order));
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceState.class, names = {"REJECTED", "PAID", "OVERDUE"}, mode = EnumSource.Mode.EXCLUDE)
    void staleInvoiceWithoutOutcome_isTakenInEveryStateButNotWhenTouchedExactlyAtTheStaleMoment(InvoiceState state) {
        PaymentOrder order = rows.created(NOW, "o-1");
        rows.invoice(order, NOW, inState(state, NOW));

        assertThat(reconciled(NOW.plusMillis(1), NEVER)).containsExactly(id(order));
        assertThat(reconciled(NOW, NEVER)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceState.class, names = {"REJECTED", "PAID", "OVERDUE"})
    void invoiceWithAnOutcome_isLeftHoweverStaleAndOverdue(InvoiceState state) {
        rows.invoice(rows.created(NOW, "o-1"), NOW, inState(state, NOW));

        assertThat(reconciled(LONG_AFTER, LONG_AFTER)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceState.class, names = "PAYMENT_ERROR", mode = EnumSource.Mode.EXCLUDE)
    void deadlineInThePast_takesNoInvoiceOtherThanAFailedPaymentWhileItIsNotStale(InvoiceState state) {
        rows.invoice(rows.created(NOW, "o-1"), NOW, inState(state, NOW));

        assertThat(reconciled(NOW, LONG_AFTER)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentOrderState.class, names = "CREATED", mode = EnumSource.Mode.EXCLUDE)
    void orderOutsideCreated_isLeftEvenWithAStaleAndOverdueFailedPayment(PaymentOrderState state) {
        PaymentOrder order = rows.order(NOW, PaymentRows.BOUNDARY, opened -> {
            created(opened);
            ReflectionTestUtils.setField(opened, "state", state);
        });
        rows.invoice(order, NOW, failedPayment(NOW));

        assertThat(reconciled(LONG_AFTER, LONG_AFTER)).isEmpty();
    }

    @Test
    void ordersToReconcile_comeInCreationOrderThenByIdentifierWithinTheLimitAndWithoutThoseThatHaveNoInvoice() {
        PaymentOrder latest = rows.created(NOW.plusSeconds(5), "o-1");
        PaymentOrder earliest = rows.created(NOW.minusSeconds(5), "o-2");
        PaymentOrder sameMoment = rows.created(NOW, "o-3");
        PaymentOrder sameMomentToo = rows.created(NOW, "o-4");
        rows.created(NOW.minusSeconds(9), "o-5");
        rows.invoice(latest, NOW, ready(NOW));
        rows.invoice(earliest, NOW, failedPayment(NOW.plusSeconds(60)));
        rows.invoice(sameMoment, NOW, failedPayment(NOW));
        rows.invoice(sameMomentToo, NOW, ready(NOW));
        List<UUID> atTheSameMoment = Stream.of(id(sameMoment), id(sameMomentToo))
                .sorted(Comparator.comparing(UUID::toString)).toList();
        Instant stale = NOW.plusMillis(1);
        Instant due = Invoices.DEADLINE.plusMillis(1);

        assertThat(reconciled(stale, due))
                .containsExactly(id(earliest), atTheSameMoment.get(0), atTheSameMoment.get(1), id(latest));
        assertThat(ids(orders.findWithInvoiceToReconcile(stale, due, Limit.of(2))))
                .containsExactly(id(earliest), atTheSameMoment.get(0));
    }

    private List<UUID> reconciled(Instant staleBefore, Instant dueBefore) {
        return ids(orders.findWithInvoiceToReconcile(staleBefore, dueBefore, Limit.of(10)));
    }

    private static Consumer<OrderInvoice> ready(Instant touched) {
        return invoice -> invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, touched);
    }

    private static Consumer<OrderInvoice> failedPayment(Instant touched) {
        return invoice -> {
            invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, touched);
            invoice.start(Invoices.DEADLINE, touched);
            invoice.issue(Invoices.LINK, touched);
            invoice.pass(InvoiceTransition.PAYMENT_FAILED, touched);
        };
    }

    private static Consumer<OrderInvoice> inState(InvoiceState state, Instant touched) {
        return invoice -> {
            invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, touched);
            invoice.start(Invoices.DEADLINE, touched);
            ReflectionTestUtils.setField(invoice, "state", state);
        };
    }

    private static void created(PaymentOrder order) {
        order.markRequested(OrderRequest.CREATION, NOW);
        order.recordOrder("o-" + UUID.randomUUID(), NOW);
    }

    private static List<UUID> ids(List<PaymentOrder> found) {
        return found.stream().map(PaymentOrderRepositoryTest::id).toList();
    }

    private static UUID id(PaymentOrder order) {
        return order.view().id();
    }
}
