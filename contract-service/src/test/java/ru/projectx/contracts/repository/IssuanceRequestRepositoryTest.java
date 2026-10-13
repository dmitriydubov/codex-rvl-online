package ru.projectx.contracts.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.IssuanceRequest;
import ru.projectx.contracts.entity.RequestParticipant;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class IssuanceRequestRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final RequestParticipant INSURED = new RequestParticipant(
            UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc"), DocumentKind.NONE);
    private static final RequestParticipant SPOUSE = new RequestParticipant(
            UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e"), DocumentKind.KM_CONTRACT);
    private static final RequestParticipant CHILD = new RequestParticipant(
            UUID.fromString("6778e8c2-985a-4d54-9bd3-0f6403b104d6"), DocumentKind.LM_POLICY);

    @Autowired
    private IssuanceRequestRepository requests;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyRequests() {
        requests.deleteAll();
    }

    @Test
    void composition_isStoredInTaskOrderAndReplacedAsAWhole() {
        transaction.executeWithoutResult(status -> {
            IssuanceRequest request = IssuanceRequest.start(REQUEST, NOW);
            request.takeComposition(List.of(SPOUSE, INSURED, CHILD), NOW);
            requests.save(request);
        });
        transaction.executeWithoutResult(status -> requests.findById(REQUEST).orElseThrow()
                .takeComposition(List.of(INSURED, CHILD), NOW.plusSeconds(1)));

        assertThat(requests.findById(REQUEST)).get().satisfies(found -> {
            assertThat(found.participants()).containsExactly(INSURED, CHILD);
        });
        assertThat(jdbc.sql("select document_kind from request_participants order by position").query(String.class).list())
                .containsExactly("NONE", "LM_POLICY");
    }

    @Test
    void cancellationAndSnapshotVersion_survivePersistence() {
        transaction.executeWithoutResult(status -> {
            IssuanceRequest request = requests.save(IssuanceRequest.start(REQUEST, NOW));
            request.cancel(NOW);
            request.nextSnapshotVersion(NOW);
        });

        assertThat(requests.findById(REQUEST)).get().satisfies(found -> {
            assertThat(found.cancelled()).isTrue();
            assertThat(found.snapshotVersion()).isEqualTo(1);
            assertThat(found.participants()).isEmpty();
        });
    }
}
