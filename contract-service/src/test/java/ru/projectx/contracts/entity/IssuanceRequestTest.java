package ru.projectx.contracts.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IssuanceRequestTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final RequestParticipant INSURED = new RequestParticipant(
            UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc"), DocumentKind.NONE);
    private static final RequestParticipant SPOUSE = new RequestParticipant(
            UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e"), DocumentKind.KM_CONTRACT);

    private final IssuanceRequest request = IssuanceRequest.start(REQUEST, NOW);

    @Test
    void startedRequest_hasNoCompositionNoSnapshotsAndIsNotCancelled() {
        assertThat(request.individualRequestId()).isEqualTo(REQUEST);
        assertThat(request.participants()).isEmpty();
        assertThat(request.snapshotVersion()).isZero();
        assertThat(request.cancelled()).isFalse();
        assertThat(request).hasFieldOrPropertyWithValue("updatedAt", NOW);
    }

    @Test
    void takeComposition_replacesParticipantsInOrderAndResumesWork() {
        request.cancel(NOW);
        request.takeComposition(List.of(SPOUSE), NOW);

        request.takeComposition(List.of(INSURED, SPOUSE), NOW.plusSeconds(1));

        assertThat(request.participants()).containsExactly(INSURED, SPOUSE);
        assertThat(request.cancelled()).isFalse();
        assertThat(request).hasFieldOrPropertyWithValue("updatedAt", NOW.plusSeconds(1));
    }

    @Test
    void cancel_marksTheRequestCancelledAndKeepsTheComposition() {
        request.takeComposition(List.of(INSURED), NOW);

        request.cancel(NOW.plusSeconds(1));

        assertThat(request.cancelled()).isTrue();
        assertThat(request.participants()).containsExactly(INSURED);
        assertThat(request).hasFieldOrPropertyWithValue("cancelledAt", NOW.plusSeconds(1))
                .hasFieldOrPropertyWithValue("updatedAt", NOW.plusSeconds(1));
    }

    @Test
    void nextSnapshotVersion_countsFromOneAndGrowsByOne() {
        assertThat(request.nextSnapshotVersion(NOW)).isEqualTo(1);
        assertThat(request.nextSnapshotVersion(NOW.plusSeconds(1))).isEqualTo(2);
        assertThat(request.snapshotVersion()).isEqualTo(2);
        assertThat(request).hasFieldOrPropertyWithValue("updatedAt", NOW.plusSeconds(1));
    }
}
