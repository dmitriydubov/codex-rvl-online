package ru.projectx.contracts.service.payment.order;

import java.util.Optional;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.service.payment.NewOrder;
import ru.projectx.contracts.service.payment.OrderingSession;

final class CreationAttempt {

    private final PaymentOrderView order;
    private final PaymentOrderRecording recording;
    private boolean requested;

    CreationAttempt(PaymentOrderView order, PaymentOrderRecording recording) {
        this.order = order;
        this.recording = recording;
        this.requested = order.creationRequested();
    }

    Optional<String> apply(OrderingSession session) {
        Optional<String> found = requested ? session.findOrder(order.clientId(), order.id()) : Optional.empty();
        if (found.isPresent()) {
            return found;
        }
        return requestAndCreate(session);
    }

    private Optional<String> requestAndCreate(OrderingSession session) {
        CreationClaim claim = recording.requestCreation(order.contractId());
        if (claim == CreationClaim.WITHDRAWN) {
            return Optional.empty();
        }
        claimAsOurs(claim);
        return Optional.of(session.createOrder(new NewOrder(order.clientId(), order.id(), order.document())));
    }

    private void claimAsOurs(CreationClaim claim) {
        boolean ours = requested;
        requested = true;
        if (claim == CreationClaim.REPEATED && !ours) {
            throw new OrderingUnavailableException(OrderingOperation.CREATE_ORDER, "создание начато другим проходом");
        }
    }
}
