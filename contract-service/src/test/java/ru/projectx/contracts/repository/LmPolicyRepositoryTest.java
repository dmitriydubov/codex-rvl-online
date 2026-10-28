package ru.projectx.contracts.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.entity.issuance.TripPeriod;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class LmPolicyRepositoryTest {

    private static final Instant OPENED = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant FIRST_SUBMITTED = Instant.parse("2026-02-10T07:10:00Z");
    private static final Instant SECOND_SUBMITTED = Instant.parse("2026-02-10T07:40:00Z");
    private static final IssuanceTerms TERMS = new IssuanceTerms(
            new RequestReference(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"),
                    UUID.fromString("e12aab1a-1582-4b3a-af36-d30e637953f1"),
                    UUID.fromString("a23ba061-f742-4b20-8b95-36a0e525c087")),
            UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc"), UUID.fromString("2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a"),
            new TripPeriod(LocalDate.parse("2026-04-10"), LocalDate.parse("2026-04-24")));

    @Autowired
    private LmPolicyRepository policies;

    @Autowired
    private ContractPersonRepository persons;

    @BeforeEach
    @AfterEach
    void emptyTables() {
        persons.deleteAll();
        policies.deleteAll();
    }

    @Test
    void firstSubmitted_isTheOldestSubmissionAmongSubmittedPoliciesOnly() {
        policies.save(submitted("6778e8c2-985a-4d54-9bd3-0f6403b104d6", SECOND_SUBMITTED));
        policies.save(submitted("2b9d4e6f-8a1c-4d3e-9f50-61728394a5b6", FIRST_SUBMITTED));
        policies.save(LmPolicy.open(new LmPolicyDraft(TERMS, UUID.fromString("4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6")),
                OPENED));

        assertThat(policies.findFirstByStatusOrderByUpdatedAtAscIdAsc(LmPolicyStatus.SUBMITTED))
                .map(policy -> policy.state().updatedAt()).contains(FIRST_SUBMITTED);
    }

    @Test
    void firstSubmitted_withoutSubmittedPolicies_isEmpty() {
        policies.save(LmPolicy.open(new LmPolicyDraft(TERMS, UUID.fromString("4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6")),
                OPENED));

        assertThat(policies.findFirstByStatusOrderByUpdatedAtAscIdAsc(LmPolicyStatus.SUBMITTED)).isEmpty();
    }

    @Test
    void dueByStatus_listsPoliciesOfThatStatusInCreationOrderUpToTheLimit() {
        LmPolicy later = LmPolicy.open(new LmPolicyDraft(TERMS, UUID.fromString("4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6")),
                OPENED.plus(Duration.ofMinutes(1)));
        LmPolicy earlier = LmPolicy.open(new LmPolicyDraft(TERMS, UUID.fromString("2b9d4e6f-8a1c-4d3e-9f50-61728394a5b6")),
                OPENED);
        policies.save(later);
        policies.save(earlier);
        policies.save(submitted("6778e8c2-985a-4d54-9bd3-0f6403b104d6", FIRST_SUBMITTED));

        assertThat(policies.findByStatusOrderByCreatedAtAscIdAsc(LmPolicyStatus.NEW, Limit.of(10)))
                .extracting(LmPolicy::participantId).containsExactly(earlier.participantId(), later.participantId());
        assertThat(policies.findByStatusOrderByCreatedAtAscIdAsc(LmPolicyStatus.NEW, Limit.of(1))).hasSize(1);
    }

    @Test
    void countBefore_excludesPoliciesSubmittedExactlyAtTheMoment() {
        policies.save(submitted("6778e8c2-985a-4d54-9bd3-0f6403b104d6", FIRST_SUBMITTED));
        policies.save(submitted("2b9d4e6f-8a1c-4d3e-9f50-61728394a5b6", SECOND_SUBMITTED));

        assertThat(policies.countByStatusAndUpdatedAtBefore(LmPolicyStatus.SUBMITTED, SECOND_SUBMITTED)).isEqualTo(1);
        assertThat(policies.countByStatusAndUpdatedAtBefore(LmPolicyStatus.SUBMITTED,
                SECOND_SUBMITTED.plus(Duration.ofSeconds(1)))).isEqualTo(2);
    }

    private static LmPolicy submitted(String participant, Instant submittedAt) {
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(TERMS, UUID.fromString(participant)), OPENED);
        policy.submit(submittedAt);
        return policy;
    }
}
