package ru.projectx.contracts.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LmPolicyTest {

    private static final Instant OPENED = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant LATER = Instant.parse("2026-02-10T07:35:00Z");
    private static final TripPeriod PERIOD = new TripPeriod(LocalDate.parse("2026-04-10"), LocalDate.parse("2026-04-24"));
    private static final UUID CHILD = UUID.fromString("6778e8c2-985a-4d54-9bd3-0f6403b104d6");
    private static final UUID POLICYHOLDER = UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc");
    private static final UUID PROGRAM = UUID.fromString("2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a");
    private static final IssuedPolicy ISSUED = new IssuedPolicy(
            UUID.fromString("cb904f2a-7d61-4e08-89a5-40b1e3c7f012"), "LM-000012204",
            UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0"), "0555123456");

    private final LmPolicy policy = LmPolicy.open(new LmPolicyDraft(new IssuanceTerms(
            new RequestReference(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"),
                    UUID.fromString("e12aab1a-1582-4b3a-af36-d30e637953f1"),
                    UUID.fromString("a23ba061-f742-4b20-8b95-36a0e525c087")),
            POLICYHOLDER, PROGRAM, PERIOD), CHILD), OPENED);

    @Test
    void open_startsInNewWithFirstVersionAndDraftRequisites() {
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.NEW);
        assertThat(policy.state().entityVersion()).isEqualTo(1);
        assertThat(policy.participantId()).isEqualTo(CHILD);
        assertThat(policy.state().policyholderParticipantId()).isEqualTo(POLICYHOLDER);
        assertThat(policy.state().programId()).contains(PROGRAM);
        assertThat(policy.state().period()).isEqualTo(PERIOD);
        assertThat(policy.state().request().insurerPolicyId()).isEqualTo(UUID.fromString("a23ba061-f742-4b20-8b95-36a0e525c087"));
        assertThat(policy.finished()).isFalse();
        assertThat(policy.submitted()).isFalse();
        assertThat(policy.issued()).isEmpty();
    }

    @Test
    void openWithoutProgram_keepsProgramEmpty() {
        LmPolicy withoutProgram = LmPolicy.open(new LmPolicyDraft(new IssuanceTerms(
                policy.state().request(), POLICYHOLDER, null, PERIOD), CHILD), OPENED);

        assertThat(withoutProgram.state().programId()).isEmpty();
    }

    @Test
    void submit_turnsNewPolicyIntoSubmittedWithNextVersion() {
        policy.submit(LATER);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.SUBMITTED);
        assertThat(policy.submitted()).isTrue();
        assertThat(policy.finished()).isFalse();
        assertThat(policy.state().entityVersion()).isEqualTo(2);
        assertThat(policy.issued()).isEmpty();
    }

    @Test
    void submit_isRefusedUnlessPolicyIsNew() {
        policy.submit(LATER);

        assertThatThrownBy(() -> policy.submit(LATER)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUBMITTED").hasMessageContaining("NEW");
        assertThat(policy.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void markIssued_storesUskIdentifiersAndOurPolicyAndFinishesIt() {
        policy.submit(LATER);

        policy.markIssued(ISSUED, LATER);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        assertThat(policy.issued()).contains(ISSUED);
        assertThat(policy.finished()).isTrue();
        assertThat(policy.submitted()).isFalse();
        assertThat(policy.state().entityVersion()).isEqualTo(3);
    }

    @Test
    void markIssued_isRefusedUnlessPolicyIsSubmitted() {
        assertThatThrownBy(() -> policy.markIssued(ISSUED, LATER)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEW").hasMessageContaining("SUBMITTED");
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.NEW);
        assertThat(policy.issued()).isEmpty();
    }

    @Test
    void markError_turnsPolicyIntoErrorAndFinishesIt() {
        policy.markError(new IssueError("UNKNOWN_DOCUMENT_TYPE", "недопустимое значение"), LATER);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ERROR);
        assertThat(policy.state().error()).map(IssueError::code).contains("UNKNOWN_DOCUMENT_TYPE");
        assertThat(policy.finished()).isTrue();
        assertThat(policy.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void cancel_turnsPolicyIntoCancelledAndFinishesIt() {
        policy.cancel(new Cancellation(CancelReason.OTHER, "участник исключён из состава поездки", LATER), LATER);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.state().cancellation()).map(Cancellation::comment).contains("участник исключён из состава поездки");
        assertThat(policy.finished()).isTrue();
    }
}
