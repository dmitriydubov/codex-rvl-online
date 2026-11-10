package ru.projectx.contracts.repository.payment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrderState;

public interface OrderInvoiceRepository extends JpaRepository<OrderInvoice, UUID> {

    Optional<OrderInvoice> findByContractId(UUID contractId);

    List<OrderInvoice> findByContractIdIn(Collection<UUID> contractIds);

    @Query("""
            select count(i) from OrderInvoice i, PaymentOrder o
             where o.id = i.orderId
               and o.state = :orderState
               and i.state in :states
               and i.createdAt < :before
            """)
    long countCreatedBefore(@Param("orderState") PaymentOrderState orderState,
            @Param("states") Collection<InvoiceState> states, @Param("before") Instant before);

    @Query("""
            select count(i) from OrderInvoice i, PaymentOrder o
             where o.id = i.orderId
               and o.state = :orderState
               and i.state in :states
               and i.paymentDeadline < :before
            """)
    long countDueBefore(@Param("orderState") PaymentOrderState orderState,
            @Param("states") Collection<InvoiceState> states, @Param("before") Instant before);
}
