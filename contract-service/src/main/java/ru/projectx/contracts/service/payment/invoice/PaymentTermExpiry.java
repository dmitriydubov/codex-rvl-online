package ru.projectx.contracts.service.payment.invoice;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLog;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentTermExpiry {

    public static final String BEFORE_ORDER = "срок оплаты истёк до создания заказа, договор прекращён";
    public static final String BEFORE_INVOICE = "срок оплаты истёк до выставления счёта, договор прекращён";
    private static final Logger log = LoggerFactory.getLogger(PaymentTermExpiry.class);

    private final RequestStatusPublication statuses;
    private final PaymentMetrics metrics;

    public PaymentTermExpiry(RequestStatusPublication statuses, PaymentMetrics metrics) {
        this.statuses = statuses;
        this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void close(LockedOrder locked, String reason, Instant now) {
        IssuanceSnapshot before = statuses.watch(locked.contract().state().request().individualRequestId());
        if (!locked.order().expire(now)) {
            locked.order().withdraw(now);
        }
        locked.contract().cancel(new Cancellation(CancelReason.EXPIRED, null, now), now);
        metrics.countTermExpired();
        PaymentOrderLog.write(log.atWarn(), locked.contract(), reason);
        statuses.afterRecordChange(before, now);
    }
}
