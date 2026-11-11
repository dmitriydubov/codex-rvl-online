package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.support.Invoices;

class PaymentOfferTest {

    private static final UUID CONTRACT = UUID.fromString("01960000-0000-7000-8000-000000000001");
    private static final PaymentOffer NOTHING = new PaymentOffer(false, Optional.empty(), Optional.empty());

    @ParameterizedTest
    @EnumSource(ContractStatus.class)
    void contractWithoutInvoice_offersNothing(ContractStatus status) {
        assertThat(PaymentOffer.of(status, Optional.empty())).isEqualTo(NOTHING);
    }

    @Test
    void policy_offersNothing() {
        assertThat(PaymentOffer.of(LmPolicyStatus.NEW, Optional.empty())).isEqualTo(NOTHING);
    }

    @Test
    void contractWithIssuedInvoice_offersTheLinkAndTheDeadline() {
        assertThat(PaymentOffer.of(ContractStatus.INVOICE_ISSUED, issued()))
                .isEqualTo(new PaymentOffer(false, Optional.of(Invoices.PAYMENT_URL), Optional.of(Invoices.DEADLINE)));
    }

    @Test
    void contractWhoseInvoiceReplyWasLost_offersTheDeadlineWithoutLink() {
        Optional<InvoiceView> lost = Optional.of(Invoices.issuedWithoutLink(CONTRACT).view());

        assertThat(PaymentOffer.of(ContractStatus.INVOICE_ISSUED, lost))
                .isEqualTo(new PaymentOffer(false, Optional.empty(), Optional.of(Invoices.DEADLINE)));
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = "INVOICE_ISSUED", mode = EnumSource.Mode.EXCLUDE)
    void contractOutsideInvoiceIssued_offersNeitherLinkNorDeadlineEvenWithAnIssuedInvoice(ContractStatus status) {
        assertThat(PaymentOffer.of(status, issued())).isEqualTo(NOTHING);
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"ACCEPTED", "INVOICE_ISSUED"})
    void deferredCancellation_hidesTheLinkAndTheDeadlineWhateverTheStatus(ContractStatus status) {
        Optional<InvoiceView> deferred = Optional.of(Invoices.cancellationDeferred(Invoices.issued(CONTRACT)).view());

        assertThat(PaymentOffer.of(status, deferred))
                .isEqualTo(new PaymentOffer(true, Optional.empty(), Optional.empty()));
    }

    private static Optional<InvoiceView> issued() {
        return Optional.of(Invoices.issued(CONTRACT).view());
    }
}
