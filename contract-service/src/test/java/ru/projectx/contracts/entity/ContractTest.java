package ru.projectx.contracts.entity;

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

class ContractTest {

    private static final Instant OPENED = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant LATER = Instant.parse("2026-02-10T07:35:00Z");
    private static final TripPeriod PERIOD = new TripPeriod(LocalDate.parse("2026-04-10"), LocalDate.parse("2026-04-24"));
    private static final TripPeriod OTHER_PERIOD =
            new TripPeriod(LocalDate.parse("2026-04-11"), LocalDate.parse("2026-04-25"));
    private static final UUID INSURED = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");
    private static final UUID POLICYHOLDER = UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc");
    private static final UUID PROGRAM = UUID.fromString("2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a");
    private static final UUID OTHER_PROGRAM = UUID.fromString("7b3d9e1f-5a2c-4d8e-b0f6-1c2a3b4d5e6f");

    private final Contract contract = Contract.open(draft("34000.00"), OPENED);

    @Test
    void open_startsInNewWithFirstVersionAndDraftRequisites() {
        assertThat(contract.status()).isEqualTo(ContractStatus.NEW);
        assertThat(contract.state().entityVersion()).isEqualTo(1);
        assertThat(contract.number()).isEqualTo("DKM-2026-000001");
        assertThat(contract.participantId()).isEqualTo(INSURED);
        assertThat(contract.state().policyholderParticipantId()).isEqualTo(POLICYHOLDER);
        assertThat(contract.state().programId()).contains(PROGRAM);
        assertThat(contract.state().period()).isEqualTo(PERIOD);
        assertThat(contract.premiumAmount()).isEqualByComparingTo("34000.00");
        assertThat(contract.state().request().individualRequestId()).isEqualTo(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"));
        assertThat(contract.id()).as("идентификатор присваивает база при сохранении").isNull();
        ReflectionTestUtils.setField(contract, "id", INSURED);
        assertThat(contract.id()).isEqualTo(INSURED);
        assertThat(contract.state().error()).isEmpty();
        assertThat(contract.state().cancellation()).isEmpty();
        assertThat(contract.finished()).isFalse();
    }

    @Test
    void revise_withSameTerms_changesNothing() {
        assertThat(contract.revise(new ContractTerms(PERIOD, new BigDecimal("34000.000")))).isFalse();
        assertThat(contract.premiumAmount()).isEqualByComparingTo("34000.00");
        assertThat(contract.state().period()).isEqualTo(PERIOD);
    }

    @Test
    void revise_withNewPremium_changesPremiumAndReportsChange() {
        assertThat(contract.revise(new ContractTerms(PERIOD, new BigDecimal("36000.00")))).isTrue();
        assertThat(contract.premiumAmount()).isEqualByComparingTo("36000.00");
    }

    @Test
    void revise_withNewPeriodOnly_movesPeriodAndReportsChange() {
        assertThat(contract.revise(new ContractTerms(OTHER_PERIOD, new BigDecimal("34000.00")))).isTrue();
        assertThat(contract.state().period()).isEqualTo(OTHER_PERIOD);
        assertThat(contract.premiumAmount()).isEqualByComparingTo("34000.00");
    }

    @Test
    void revise_withNewPeriodAndPremium_changesBoth() {
        assertThat(contract.revise(new ContractTerms(OTHER_PERIOD, new BigDecimal("36000.00")))).isTrue();
        assertThat(contract.state().period()).isEqualTo(OTHER_PERIOD);
        assertThat(contract.premiumAmount()).isEqualByComparingTo("36000.00");
        assertThat(contract.state().entityVersion()).isEqualTo(1);
    }

    @Test
    void movePeriod_reportsWhetherPeriodChangedWithoutTouchingVersion() {
        assertThat(contract.movePeriod(PERIOD)).isFalse();
        assertThat(contract.movePeriod(OTHER_PERIOD)).isTrue();
        assertThat(contract.state().period()).isEqualTo(OTHER_PERIOD);
        assertThat(contract.state().entityVersion()).isEqualTo(1);
    }

    @Test
    void advanceVersion_bumpsVersionOnce() {
        contract.advanceVersion(LATER);

        assertThat(contract.state().entityVersion()).isEqualTo(2);
        assertThat(contract).hasFieldOrPropertyWithValue("updatedAt", LATER);
    }

    @Test
    void markError_turnsContractIntoErrorWithCodeAndFinishesIt() {
        contract.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), LATER);

        assertThat(contract.status()).isEqualTo(ContractStatus.ERROR);
        assertThat(contract.state().error()).contains(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"));
        assertThat(contract.state().entityVersion()).isEqualTo(2);
        assertThat(contract.finished()).isTrue();
    }

    @Test
    void cancel_turnsContractIntoCancelledWithReasonAndFinishesIt() {
        Cancellation cancellation = new Cancellation(CancelReason.EXPIRED, null, LATER);

        contract.cancel(cancellation, LATER);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(cancellation);
        assertThat(contract.state().entityVersion()).isEqualTo(2);
        assertThat(contract.finished()).isTrue();
    }

    @Test
    void offerDraft_makesNewContractReadyForAcceptanceWithoutNewVersion() {
        contract.offerDraft();

        assertThat(contract.status()).isEqualTo(ContractStatus.DRAFT_READY);
        assertThat(contract.state().entityVersion()).isEqualTo(1);
        assertThat(contract.finished()).isFalse();
    }

    @Test
    void offerDraft_leavesContractInErrorAsItIs() {
        contract.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), LATER);

        contract.offerDraft();

        assertThat(contract.status()).isEqualTo(ContractStatus.ERROR);
        assertThat(contract.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void offerDraft_leavesCancelledContractAsItIs() {
        contract.cancel(new Cancellation(CancelReason.EXPIRED, null, LATER), LATER);

        contract.offerDraft();

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
    }

    @Test
    void offerDraft_onContractReadyForAcceptance_keepsItReady() {
        contract.offerDraft();

        contract.offerDraft();

        assertThat(contract.status()).isEqualTo(ContractStatus.DRAFT_READY);
        assertThat(contract.state().entityVersion()).isEqualTo(1);
    }

    @Test
    void moveProgram_changesOnlyAnotherProgram() {
        assertThat(contract.moveProgram(PROGRAM)).isFalse();
        assertThat(contract.moveProgram(OTHER_PROGRAM)).isTrue();
        assertThat(contract.state().programId()).contains(OTHER_PROGRAM);
    }

    @Test
    void accept_turnsContractReadyForAcceptanceIntoAcceptedWithoutNewVersion() {
        contract.offerDraft();

        contract.accept();

        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        assertThat(contract.state().entityVersion()).isEqualTo(1);
        assertThat(contract.offerAccepted()).isTrue();
        assertThat(contract.finished()).isFalse();
    }

    @Test
    void accept_isRefusedOutsideReadyForAcceptanceAndChangesNothing() {
        assertThatThrownBy(() -> contract.accept()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEW");

        assertThat(contract.status()).isEqualTo(ContractStatus.NEW);
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"ACCEPTED", "INVOICE_ISSUED", "PAID", "SUBMITTED"})
    void offerAccepted_holdsFromAcceptanceUntilIssue(ContractStatus status) {
        ReflectionTestUtils.setField(contract, "status", status);

        assertThat(contract.offerAccepted()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"NEW", "DRAFT_READY", "ISSUED", "ERROR", "CANCELLED"})
    void offerAccepted_doesNotHoldBeforeAcceptanceOrAfterTheEnd(ContractStatus status) {
        ReflectionTestUtils.setField(contract, "status", status);

        assertThat(contract.offerAccepted()).isFalse();
    }

    private static ContractDraft draft(String premium) {
        RequestReference request = new RequestReference(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"),
                UUID.fromString("e12aab1a-1582-4b3a-af36-d30e637953f1"),
                UUID.fromString("a23ba061-f742-4b20-8b95-36a0e525c087"));
        return new ContractDraft("DKM-2026-000001", new IssuanceTerms(request, POLICYHOLDER, PROGRAM, PERIOD), INSURED,
                new BigDecimal(premium));
    }
}
