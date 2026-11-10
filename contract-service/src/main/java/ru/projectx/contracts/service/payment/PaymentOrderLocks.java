package ru.projectx.contracts.service.payment;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;

@Component
public class PaymentOrderLocks {

    private final ContractRepository contracts;
    private final PaymentOrderRepository orders;

    private final OrderInvoiceRepository invoices;

    public PaymentOrderLocks(ContractRepository contracts, PaymentOrderRepository orders,
            OrderInvoiceRepository invoices) {
        this.contracts = contracts;
        this.orders = orders;
        this.invoices = invoices;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LockedOrder lock(UUID contractId) {
        Contract contract = contracts.findLockedById(contractId)
                .orElseThrow(() -> new IllegalStateException("договора КМ " + contractId + " нет"));
        PaymentOrder order = orders.findLockedByContractId(contractId)
                .orElseThrow(() -> new IllegalStateException("у договора КМ " + contractId + " нет заказа на оплату"));
        return new LockedOrder(contract, order);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LockedInvoice lockInvoice(UUID contractId) {
        LockedOrder locked = lock(contractId);
        OrderInvoice invoice = invoices.findByContractId(contractId)
                .orElseThrow(() -> new IllegalStateException(
                        "у заказа на оплату договора КМ " + contractId + " нет счёта"));
        return new LockedInvoice(locked.contract(), locked.order(), invoice);
    }
}
