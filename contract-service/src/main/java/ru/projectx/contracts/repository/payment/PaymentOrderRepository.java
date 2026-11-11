package ru.projectx.contracts.repository.payment;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderKeys;
import ru.projectx.contracts.entity.payment.PaymentOrderState;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Optional<PaymentOrder> findByContractId(UUID contractId);

    @Query("select new ru.projectx.contracts.entity.payment.PaymentOrderKeys(o.contractId, o.correlationId)"
            + " from PaymentOrder o where o.id = :id")
    Optional<PaymentOrderKeys> findKeysById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.contractId = :contractId")
    Optional<PaymentOrder> findLockedByContractId(@Param("contractId") UUID contractId);

    List<PaymentOrder> findByStateOrderByCreatedAtAscIdAsc(PaymentOrderState state, Limit limit);

    long countByStateAndCreatedAtBefore(PaymentOrderState state, Instant threshold);

    @Query("select new ru.projectx.contracts.entity.payment.PaymentOrderKeys(o.contractId, o.correlationId)"
            + " from PaymentOrder o where o.externalOrderId = :orderId")
    Optional<PaymentOrderKeys> findKeysByExternalOrderId(@Param("orderId") String orderId);

    @Query("""
            select o from PaymentOrder o
             where o.state = :state
               and exists (select 1 from OrderInvoice i where i.orderId = o.id and i.state = :invoiceState)
             order by o.createdAt, o.id
            """)
    List<PaymentOrder> findWithInvoiceIn(@Param("state") PaymentOrderState state,
            @Param("invoiceState") InvoiceState invoiceState, Limit limit);

    @Query("""
            select o from PaymentOrder o
             where o.state in :states
               and o.document.documentTtl < :now
               and not exists (select 1 from OrderInvoice i where i.orderId = o.id and i.state in :invoiced)
             order by o.createdAt, o.id
            """)
    List<PaymentOrder> findPastBoundary(@Param("states") Collection<PaymentOrderState> states,
            @Param("invoiced") Collection<InvoiceState> invoiced, @Param("now") Instant now, Limit limit);

    default List<PaymentOrder> findWithInvoiceToReconcile(Instant staleBefore, Instant dueBefore, Limit limit) {
        return findCreatedWithInvoiceToReconcile(staleBefore, dueBefore, limit.max());
    }

    @Query(value = """
            select o.*
              from payment_orders o
             where o.state = 'CREATED'
               and exists (select 1 from payment_invoices i
                            where i.payment_order_id = o.id
                              and ((i.state not in ('REJECTED', 'PAID', 'OVERDUE') and i.updated_at < :staleBefore)
                                   or (i.state = 'PAYMENT_ERROR' and i.payment_deadline < :dueBefore)))
             order by o.created_at, o.id
             limit :batch
            """, nativeQuery = true)
    List<PaymentOrder> findCreatedWithInvoiceToReconcile(@Param("staleBefore") Instant staleBefore,
            @Param("dueBefore") Instant dueBefore, @Param("batch") int batch);
}
