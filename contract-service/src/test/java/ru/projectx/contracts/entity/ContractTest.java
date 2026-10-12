package ru.projectx.contracts.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContractTest {

    private static final Instant OPENED = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant LATER = Instant.parse("2026-02-10T07:35:00Z");
    private static final TripPeriod PERIOD = new TripPeriod(LocalDate.parse("2026-04-10"), LocalDate.parse("2026-04-24"));
    private static final TripPeriod OTHER_PERIOD =
            new TripPeriod(LocalDate.parse("2026-04-11"), LocalDate.parse("2026-04-25"));
    private static final UUID INSURED = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");
    private static final UUID POLICYHOLDER = UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc");

    private final Contract contract = Contract.open(draft("34000.00"), OPENED);

    @Test
    void open_startsInNewWithFirstVersionAndDraftRequisites() {
        assertThat(contract.status()).isEqualTo(ContractStatus.NEW);
        assertThat(contract.state().entityVersion()).isEqualTo(1);
        assertThat(contract.number()).isEqualTo("DKM-2026-000001");
        assertThat(contract.insuredParticipantId()).isEqualTo(INSURED);
        assertThat(contract.state().policyholderParticipantId()).isEqualTo(POLICYHOLDER);
        assertThat(contract.state().period()).isEqualTo(PERIOD);
        assertThat(contract.premiumAmount()).isEqualByComparingTo("34000.00");
        assertThat(contract.state().request().individualRequestId()).isEqualTo(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"));
        assertThat(contract.id()).as("идентификатор присваивает база при сохранении").isNull();
        assertThat(contract.state().error()).isEmpty();
        assertThat(contract.state().cancellation()).isEmpty();
        assertThat(contract.finished()).isFalse();
    }

    @Test
    void revise_withSameTerms_keepsVersion() {
        contract.revise(new ContractTerms(PERIOD, new BigDecimal("34000.000")), LATER);

        assertThat(contract.state().entityVersion()).isEqualTo(1);
        assertThat(contract.premiumAmount()).isEqualByComparingTo("34000.00");
    }

    @Test
    void revise_withNewPremium_changesPremiumAndBumpsVersionOnce() {
        contract.revise(new ContractTerms(PERIOD, new BigDecimal("36000.00")), LATER);

        assertThat(contract.premiumAmount()).isEqualByComparingTo("36000.00");
        assertThat(contract.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void revise_withNewPeriodAndPremium_bumpsVersionTwice() {
        contract.revise(new ContractTerms(OTHER_PERIOD, new BigDecimal("36000.00")), LATER);

        assertThat(contract.state().period()).isEqualTo(OTHER_PERIOD);
        assertThat(contract.state().entityVersion()).isEqualTo(3);
    }

    @Test
    void movePeriod_reportsWhetherPeriodChanged() {
        assertThat(contract.movePeriod(PERIOD, LATER)).isFalse();
        assertThat(contract.movePeriod(OTHER_PERIOD, LATER)).isTrue();
        assertThat(contract.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void markPersonsReplaced_bumpsVersion() {
        contract.markPersonsReplaced(LATER);

        assertThat(contract.state().entityVersion()).isEqualTo(2);
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

    private static ContractDraft draft(String premium) {
        RequestReference request = new RequestReference(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"),
                UUID.fromString("e12aab1a-1582-4b3a-af36-d30e637953f1"),
                UUID.fromString("a23ba061-f742-4b20-8b95-36a0e525c087"));
        return new ContractDraft("DKM-2026-000001", request, INSURED, POLICYHOLDER, PERIOD, new BigDecimal(premium));
    }
}
