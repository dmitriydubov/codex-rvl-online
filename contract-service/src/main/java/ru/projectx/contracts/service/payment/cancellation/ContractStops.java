package ru.projectx.contracts.service.payment.cancellation;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLog;

@Service
public class ContractStops {

    static final String DEFERRED = "договор со счётом: прекращение отложено до просрочки оплаты";
    static final String PAID_STOPPED = "работа по оплаченному договору прекращена: заказ не отменяется, деньги возвращают";
    private static final Logger log = LoggerFactory.getLogger(ContractStops.class);

    private final PaymentOrderRepository orders;
    private final OrderInvoiceRepository invoices;
    private final PaymentMetrics metrics;

    public ContractStops(PaymentOrderRepository orders, OrderInvoiceRepository invoices, PaymentMetrics metrics) {
        this.orders = orders;
        this.invoices = invoices;
        this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void stop(Contract contract, Cancellation cancellation, Instant now) {
        Optional<PaymentOrder> order = orders.findLockedByContractId(contract.id());
        Optional<OrderInvoice> invoice = order.flatMap(locked -> invoices.findFreshByContractId(contract.id()));
        if (awaitsPayment(contract, invoice)) {
            invoice.orElseThrow().deferCancellation(cancellation, now);
            PaymentOrderLog.write(log.atInfo(), contract, DEFERRED);
            return;
        }
        contract.cancel(cancellation, now);
        if (contract.progress().paidAt().isPresent()) {
            metrics.countPaidCancelled();
            PaymentOrderLog.write(log.atInfo(), contract, PAID_STOPPED);
            return;
        }
        order.filter(stopped -> !paid(invoice)).ifPresent(stopped -> stopped.withdraw(now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void resume(Contract contract, Instant now) {
        if (orders.findLockedByContractId(contract.id()).isPresent()) {
            invoices.findFreshByContractId(contract.id()).ifPresent(invoice -> invoice.liftDeferredCancellation(now));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Set<UUID> deferred(Collection<UUID> contractIds) {
        return invoices.findByContractIdIn(contractIds).stream()
                .map(OrderInvoice::view)
                .filter(InvoiceView::cancellationDeferred)
                .map(InvoiceView::contractId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean awaitsPayment(Contract contract, Optional<OrderInvoice> invoice) {
        return contract.status().awaitsPayment()
                && invoice.map(OrderInvoice::view).filter(InvoiceView::awaitsPayment).isPresent();
    }

    private static boolean paid(Optional<OrderInvoice> invoice) {
        return invoice.map(OrderInvoice::view).filter(view -> view.state() == InvoiceState.PAID).isPresent();
    }
}
