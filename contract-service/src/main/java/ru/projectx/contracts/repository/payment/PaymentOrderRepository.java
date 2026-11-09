package ru.projectx.contracts.repository.payment;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Optional<PaymentOrder> findByContractId(UUID contractId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.contractId = :contractId")
    Optional<PaymentOrder> findLockedByContractId(@Param("contractId") UUID contractId);

    List<PaymentOrder> findByStateOrderByCreatedAtAscIdAsc(PaymentOrderState state, Limit limit);

    long countByStateAndCreatedAtBefore(PaymentOrderState state, Instant threshold);
}
