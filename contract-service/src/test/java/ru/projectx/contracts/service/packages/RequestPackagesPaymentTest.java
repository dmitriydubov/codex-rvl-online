package ru.projectx.contracts.service.packages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.dto.PackageItem;
import ru.projectx.contracts.dto.PackageItem.PackagePayment;
import ru.projectx.contracts.dto.PackageItem.PaymentState;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.repository.issuance.ContractAcceptanceRepository;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.IssuanceSnapshots;
import ru.projectx.contracts.support.Invoices;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class RequestPackagesPaymentTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");
    private static final String CONTRACT_ID = "3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4";
    private static final String NUMBER = "DKM-2026-000001";
    private static final BigDecimal PREMIUM = new BigDecimal("34000.00");
    private static final RequestParticipant SPOUSE = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);

    private final IssuanceRequestRepository requests = mock();
    private final IssuanceSnapshots snapshots = mock();
    private final ContractAcceptanceRepository acceptances = mock();
    private final RequestPackages packages = new RequestPackages(requests, snapshots, acceptances);
    private final Contract invoiced = Issuances.invoicedContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);

    @BeforeEach
    void requestIsKnown() {
        given(requests.existsById(Tasks.REQUEST)).willReturn(true);
        given(acceptances.findByContractIdIn(anyCollection())).willReturn(List.of());
    }

    @Test
    void contractWithInvoiceLink_isInvoiceIssuedWithPendingPaymentLinkAndDeadline() {
        PackageItem item = itemOf(invoiced, Invoices.issued(invoiced.id()));

        assertThat(item.state()).isEqualTo(DocumentState.INVOICE_ISSUED);
        assertThat(item.payment()).isEqualTo(
                new PackagePayment(PaymentState.PENDING, PREMIUM, Invoices.PAYMENT_URL, Invoices.DEADLINE));
    }

    @Test
    void contractWhoseInvoiceReplyWasLost_isInvoiceIssuedWithPendingPaymentAndDeadlineWithoutLink() {
        PackageItem item = itemOf(invoiced, Invoices.issuedWithoutLink(invoiced.id()));

        assertThat(item.state()).isEqualTo(DocumentState.INVOICE_ISSUED);
        assertThat(item.payment()).isEqualTo(new PackagePayment(PaymentState.PENDING, PREMIUM, null, Invoices.DEADLINE));
    }

    @Test
    void contractWithDeferredCancellation_isCancelledWithoutPaymentBlockOrError() {
        PackageItem item = itemOf(invoiced, Invoices.cancellationDeferred(Invoices.issued(invoiced.id())));

        assertThat(item.state()).isEqualTo(DocumentState.CANCELLED);
        assertThat(item.payment()).isNull();
        assertThat(item.errorCode()).isNull();
        assertThat(item.contractNumber()).isEqualTo(NUMBER);
        assertThat(item.premiumAmount()).isEqualByComparingTo(PREMIUM);
    }

    @Test
    void acceptedContractWithInvoiceInCheckout_isAcceptedWithPendingPaymentWithoutLinkAndDeadline() {
        Contract accepted = Issuances.acceptedContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);

        PackageItem item = itemOf(accepted, Invoices.inCheckout(accepted.id()));

        assertThat(item.state()).isEqualTo(DocumentState.ACCEPTED);
        assertThat(item.payment()).isEqualTo(new PackagePayment(PaymentState.PENDING, PREMIUM, null, null));
    }

    @Test
    void contractFailedAfterPayment_isInErrorWithItsCodeAndThePaidBlock() {
        invoiced.markPaid(PAID_AT, NOW);
        invoiced.markError(new IssueError("KM_CONTRACT_SUBMISSION_UNDELIVERED", "договор КМ не передан в 1С:УСК"), NOW);

        PackageItem item = itemOf(invoiced, Invoices.issued(invoiced.id()));

        assertThat(item.state()).isEqualTo(DocumentState.ERROR);
        assertThat(item.errorCode()).isEqualTo("KM_CONTRACT_SUBMISSION_UNDELIVERED");
        assertThat(item.payment()).isEqualTo(new PackagePayment(PaymentState.PAID, PREMIUM, null, null));
    }

    @Test
    void contractCancelledAfterPayment_keepsThePaidBlockAndTheOneCancelledWithoutPaymentHasNone() {
        Contract paid = Issuances.paidContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER, PAID_AT);
        paid.cancel(new Cancellation(CancelReason.INSURED_REFUSED, null, NOW), NOW);
        invoiced.cancel(new Cancellation(CancelReason.EXPIRED, null, NOW), NOW);

        assertThat(itemOf(paid, Invoices.issued(paid.id())))
                .returns(DocumentState.CANCELLED, PackageItem::state)
                .returns(new PackagePayment(PaymentState.PAID, PREMIUM, null, null), PackageItem::payment);
        assertThat(itemOf(invoiced, Invoices.issued(invoiced.id())))
                .returns(DocumentState.CANCELLED, PackageItem::state)
                .returns(null, PackageItem::payment);
    }

    private PackageItem itemOf(Contract contract, OrderInvoice invoice) {
        given(snapshots.current(Tasks.REQUEST)).willReturn(new IssuanceSnapshot(Tasks.REQUEST, false, List.of(SPOUSE),
                List.of(contract), List.of(), List.of(), List.of(invoice.view())));
        return packages.packageOf(Tasks.REQUEST).items().get(0);
    }
}
