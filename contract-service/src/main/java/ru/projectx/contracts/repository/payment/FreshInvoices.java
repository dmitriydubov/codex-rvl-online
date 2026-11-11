package ru.projectx.contracts.repository.payment;

import java.util.Optional;
import java.util.UUID;
import ru.projectx.contracts.entity.payment.OrderInvoice;

public interface FreshInvoices {

    Optional<OrderInvoice> findFreshByContractId(UUID contractId);
}
