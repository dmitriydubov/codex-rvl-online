package ru.projectx.contracts.repository.payment;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.contracts.entity.payment.OrderInvoice;

class FreshInvoicesImpl implements FreshInvoices {

    private static final String BY_CONTRACT = "select i from OrderInvoice i where i.contractId = :contractId";
    private static final String CONTRACT_ID = "contractId";

    private final EntityManager entityManager;

    FreshInvoicesImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<OrderInvoice> findFreshByContractId(UUID contractId) {
        Optional<OrderInvoice> invoice = entityManager.createQuery(BY_CONTRACT, OrderInvoice.class)
                .setParameter(CONTRACT_ID, contractId)
                .getResultList()
                .stream()
                .findFirst();
        invoice.ifPresent(entityManager::refresh);
        return invoice;
    }
}
