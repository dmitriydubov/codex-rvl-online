package ru.projectx.contracts.service.payment.invoice;

import java.util.UUID;
import ru.projectx.contracts.entity.payment.InvoiceLink;

sealed interface CheckoutOutcome {

    void recordTo(InvoiceRecording recording, UUID contractId);

    record Issued(InvoiceLink link) implements CheckoutOutcome {

        @Override
        public void recordTo(InvoiceRecording recording, UUID contractId) {
            recording.issued(contractId, link);
        }
    }

    record ReplyLost() implements CheckoutOutcome {

        @Override
        public void recordTo(InvoiceRecording recording, UUID contractId) {
            recording.replyLost(contractId);
        }
    }
}
