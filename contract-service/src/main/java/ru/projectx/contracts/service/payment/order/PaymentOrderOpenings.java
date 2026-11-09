package ru.projectx.contracts.service.payment.order;

import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.OfferAcceptance;
import ru.projectx.contracts.entity.payment.OrderDocument;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.payment.PaymentOrderOpening;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderOpenings implements PaymentOrderOpening {

    private final PaymentOrderRepository orders;
    private final OrderingProperties.Document settings;

    public PaymentOrderOpenings(PaymentOrderRepository orders, OrderingProperties properties) {
        this.orders = orders;
        this.settings = properties.document();
    }

    @Override
    public void open(Contract contract, OfferAcceptance offer, Instant now) {
        OrderDocument document = new OrderDocument(contract.number(), contract.premiumAmount(), offer.acceptedAt(),
                ttl(contract));
        orders.save(PaymentOrder.open(contract.id(), contract.state().policyholderParticipantId(), document, now));
    }

    private Instant ttl(Contract contract) {
        ZoneId zone = settings.zone();
        return contract.state().period().dateFrom().atStartOfDay(zone).toInstant()
                .minus(settings.ttlBeforeCheckIn());
    }
}
