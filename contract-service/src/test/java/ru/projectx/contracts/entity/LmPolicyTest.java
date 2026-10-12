package ru.projectx.contracts.entity;

import static org.assertj.core.api.Assertions.assertThat;

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

    private final LmPolicy policy = LmPolicy.open(new LmPolicyDraft(
            new RequestReference(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"),
                    UUID.fromString("e12aab1a-1582-4b3a-af36-d30e637953f1"),
                    UUID.fromString("a23ba061-f742-4b20-8b95-36a0e525c087")),
            CHILD, POLICYHOLDER, PERIOD), OPENED);

    @Test
    void open_startsInNewWithFirstVersionAndDraftRequisites() {
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.NEW);
        assertThat(policy.state().entityVersion()).isEqualTo(1);
        assertThat(policy.participantId()).isEqualTo(CHILD);
        assertThat(policy.state().policyholderParticipantId()).isEqualTo(POLICYHOLDER);
        assertThat(policy.state().period()).isEqualTo(PERIOD);
        assertThat(policy.state().request().insurerPolicyId()).isEqualTo(UUID.fromString("a23ba061-f742-4b20-8b95-36a0e525c087"));
        assertThat(policy.finished()).isFalse();
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
