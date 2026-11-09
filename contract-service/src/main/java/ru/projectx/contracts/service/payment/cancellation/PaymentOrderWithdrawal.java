package ru.projectx.contracts.service.payment.cancellation;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;

@Service
public class PaymentOrderWithdrawal {

    private final PaymentOrderRepository orders;

    public PaymentOrderWithdrawal(PaymentOrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void contractStopped(UUID contractId, Instant now) {
        orders.findLockedByContractId(contractId).ifPresent(order -> order.withdraw(now));
    }
}
