package ru.projectx.contracts.entity.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.entity.issuance.TripPeriod;

class ContractStepTest {

    private static final UUID CONTRACT = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
    private static final UUID PARTICIPANT = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");
    private static final IssuanceTerms TERMS = new IssuanceTerms(
            new RequestReference(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"),
                    UUID.fromString("e12aab1a-1582-4b3a-af36-d30e637953f1"),
                    UUID.fromString("a23ba061-f742-4b20-8b95-36a0e525c087")),
            UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc"),
            UUID.fromString("2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a"),
            new TripPeriod(LocalDate.parse("2026-04-10"), LocalDate.parse("2026-04-24")));

    private final Contract contract = Contract.open(
            new ContractDraft("DKM-2026-000001", TERMS, PARTICIPANT, new BigDecimal("34000.00")),
            Instant.parse("2026-02-10T07:05:00Z"));

    @Test
    void offerAcceptance_leadsFromDraftReadyToAccepted() {
        assertThat(ContractStep.OFFER_ACCEPTED.after(ContractStatus.DRAFT_READY, CONTRACT))
                .isEqualTo(ContractStatus.ACCEPTED);
    }

    @Test
    void invoiceIssue_leadsFromAcceptedToInvoiceIssued() {
        assertThat(ContractStep.INVOICE_ISSUED.after(ContractStatus.ACCEPTED, CONTRACT))
                .isEqualTo(ContractStatus.INVOICE_ISSUED);
    }

    @Test
    void offerAcceptance_turnsTheContractReadyForAcceptanceIntoAcceptedWithoutNewVersion() {
        ReflectionTestUtils.setField(contract, "status", ContractStatus.DRAFT_READY);

        contract.advance(ContractStep.OFFER_ACCEPTED);

        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        assertThat(contract.state().entityVersion()).isEqualTo(1);
    }

    @Test
    void invoiceIssue_turnsAcceptedContractIntoInvoiceIssuedThatStillAwaitsPaymentWithoutNewVersion() {
        ReflectionTestUtils.setField(contract, "status", ContractStatus.ACCEPTED);

        contract.advance(ContractStep.INVOICE_ISSUED);

        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        assertThat(contract.status().awaitsPayment()).isTrue();
        assertThat(contract.status().offerAccepted()).isTrue();
        assertThat(contract.state().entityVersion()).isEqualTo(1);
        assertThat(contract.finished()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = "ACCEPTED", mode = EnumSource.Mode.EXCLUDE)
    void invoiceIssue_isRefusedUnlessTheContractIsAcceptedAndChangesNothing(ContractStatus status) {
        ReflectionTestUtils.setField(contract, "id", CONTRACT);
        ReflectionTestUtils.setField(contract, "status", status);

        assertThatThrownBy(() -> contract.advance(ContractStep.INVOICE_ISSUED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("договор КМ " + CONTRACT + " в статусе " + status + ", а не ACCEPTED");
        assertThat(contract.status()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = "DRAFT_READY", mode = EnumSource.Mode.EXCLUDE)
    void offerAcceptance_isRefusedUnlessTheContractIsReadyForAcceptanceAndChangesNothing(ContractStatus status) {
        ReflectionTestUtils.setField(contract, "id", CONTRACT);
        ReflectionTestUtils.setField(contract, "status", status);

        assertThatThrownBy(() -> contract.advance(ContractStep.OFFER_ACCEPTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("договор КМ " + CONTRACT + " в статусе " + status + ", а не DRAFT_READY");
        assertThat(contract.status()).isEqualTo(status);
    }
}
