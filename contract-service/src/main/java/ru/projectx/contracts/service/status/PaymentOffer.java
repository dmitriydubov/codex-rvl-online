package ru.projectx.contracts.service.status;

import java.time.Instant;
import java.util.Optional;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.entity.payment.InvoiceLink;
import ru.projectx.contracts.entity.payment.InvoiceTerm;
import ru.projectx.contracts.entity.payment.InvoiceView;

public record PaymentOffer(boolean cancellationDeferred, Optional<String> link, Optional<Instant> deadline) {

    private static final PaymentOffer NONE = new PaymentOffer(false, Optional.empty(), Optional.empty());

    static PaymentOffer of(IssuanceStatus status, Optional<InvoiceView> invoice) {
        return invoice.map(known -> of(status, known)).orElse(NONE);
    }

    private static PaymentOffer of(IssuanceStatus status, InvoiceView invoice) {
        if (invoice.cancellationDeferred()) {
            return new PaymentOffer(true, Optional.empty(), Optional.empty());
        }
        if (status != ContractStatus.INVOICE_ISSUED) {
            return NONE;
        }
        return new PaymentOffer(false, invoice.link().map(InvoiceLink::paymentUrl),
                invoice.term().map(InvoiceTerm::deadline));
    }
}
