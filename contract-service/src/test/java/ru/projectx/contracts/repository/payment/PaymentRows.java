package ru.projectx.contracts.repository.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.payment.OrderDocument;
import ru.projectx.contracts.entity.payment.OrderDraft;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.OrderRequest;
import ru.projectx.contracts.entity.payment.PayerEmail;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

final class PaymentRows {

    static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    static final Instant BOUNDARY = Instant.parse("2026-04-09T20:59:59Z");
    static final String PAYER = "insured@example.test";

    private final ContractRepository contracts;
    private final PaymentOrderRepository orders;
    private final OrderInvoiceRepository invoices;
    private final TransactionTemplate transaction;
    private int numbered;

    PaymentRows(ContractRepository contracts, PaymentOrderRepository orders, OrderInvoiceRepository invoices,
            TransactionTemplate transaction) {
        this.contracts = contracts;
        this.orders = orders;
        this.invoices = invoices;
        this.transaction = transaction;
    }

    void deleteAll() {
        invoices.deleteAll();
        orders.deleteAll();
        contracts.deleteAll();
    }

    PaymentOrder order(Instant createdAt, Instant boundary, Consumer<PaymentOrder> state) {
        return orderOf(new PayerEmail(PAYER), createdAt, boundary, state);
    }

    PaymentOrder orderOf(PayerEmail payer, Instant createdAt, Instant boundary, Consumer<PaymentOrder> state) {
        numbered++;
        String number = "DKM-2026-%06d".formatted(numbered);
        return transaction.execute(status -> {
            Contract contract = contracts.save(Contract.open(new ContractDraft(number, Issuances.TERMS,
                    UUID.randomUUID(), new BigDecimal("34000.00")), createdAt));
            PaymentOrder order = PaymentOrder.open(new OrderDraft(contract.id(), Tasks.INSURED,
                    new OrderDocument(number, new BigDecimal("34000.00"), createdAt, boundary), payer), createdAt);
            state.accept(order);
            return orders.save(order);
        });
    }

    PaymentOrder created(Instant createdAt, String orderId) {
        return order(createdAt, BOUNDARY, order -> {
            order.markRequested(OrderRequest.CREATION, createdAt);
            order.recordOrder(orderId, createdAt);
        });
    }

    OrderInvoice invoice(PaymentOrder order, Instant createdAt, Consumer<OrderInvoice> state) {
        OrderInvoice invoice = OrderInvoice.open(order.view().id(), order.view().contractId(), createdAt);
        state.accept(invoice);
        return transaction.execute(status -> invoices.save(invoice));
    }
}
