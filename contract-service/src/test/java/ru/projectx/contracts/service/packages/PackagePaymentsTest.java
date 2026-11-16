package ru.projectx.contracts.service.packages;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.dto.PackageItem.PackagePayment;
import ru.projectx.contracts.dto.PackageItem.PaymentState;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.service.status.PaymentOffer;
import ru.projectx.contracts.support.Invoices;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class PackagePaymentsTest {

    private static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");
    private static final String CONTRACT_ID = "3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4";
    private static final String NUMBER = "DKM-2026-000001";
    private static final BigDecimal PREMIUM = new BigDecimal("34000.00");
    private static final PaymentOffer NOTHING = new PaymentOffer(false, Optional.empty(), Optional.empty());
    private static final PaymentOffer LINKED =
            new PaymentOffer(false, Optional.of(Invoices.PAYMENT_URL), Optional.of(Invoices.DEADLINE));

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"PAID", "SUBMITTED", "ISSUED", "ERROR", "CANCELLED"})
    void contractWithTheMomentOfPayment_isPaidWithItsPremiumWhateverItsStatus(ContractStatus status) {
        Contract paid = Issuances.paidContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER, PAID_AT);
        ReflectionTestUtils.setField(paid, "status", status);

        assertThat(PackagePayments.of(paid, PackageStates.of(status), NOTHING))
                .isEqualTo(new PackagePayment(PaymentState.PAID, PREMIUM, null, null));
    }

    @Test
    void paidContract_showsNeitherTheLinkNorTheDeadlineOfItsInvoice() {
        Contract paid = Issuances.paidContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER, PAID_AT);

        assertThat(PackagePayments.of(paid, DocumentState.INVOICE_ISSUED, LINKED))
                .isEqualTo(new PackagePayment(PaymentState.PAID, PREMIUM, null, null));
    }

    @Test
    void acceptedContract_awaitsPaymentOfItsPremiumWithoutLinkAndDeadline() {
        Contract accepted = Issuances.acceptedContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);

        assertThat(PackagePayments.of(accepted, DocumentState.ACCEPTED, LINKED))
                .isEqualTo(new PackagePayment(PaymentState.PENDING, PREMIUM, null, null));
    }

    @Test
    void invoicedContract_awaitsPaymentWithTheLinkAndTheDeadline() {
        Contract invoiced = Issuances.invoicedContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);

        assertThat(PackagePayments.of(invoiced, DocumentState.INVOICE_ISSUED, LINKED))
                .isEqualTo(new PackagePayment(PaymentState.PENDING, PREMIUM, Invoices.PAYMENT_URL, Invoices.DEADLINE));
    }

    @Test
    void invoicedContractWhoseReplyWasLost_awaitsPaymentWithTheDeadlineButWithoutLink() {
        Contract invoiced = Issuances.invoicedContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);
        PaymentOffer lost = new PaymentOffer(false, Optional.empty(), Optional.of(Invoices.DEADLINE));

        assertThat(PackagePayments.of(invoiced, DocumentState.INVOICE_ISSUED, lost))
                .isEqualTo(new PackagePayment(PaymentState.PENDING, PREMIUM, null, Invoices.DEADLINE));
    }

    @ParameterizedTest
    @EnumSource(value = DocumentState.class, names = {"ACCEPTED", "INVOICE_ISSUED"}, mode = EnumSource.Mode.EXCLUDE)
    void unpaidContractShownOutsideAcceptanceAndInvoice_hasNoPaymentBlock(DocumentState shown) {
        Contract invoiced = Issuances.invoicedContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);

        assertThat(PackagePayments.of(invoiced, shown, LINKED)).isNull();
    }
}
