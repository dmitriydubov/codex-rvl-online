package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractDraft;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.RequestReference;
import ru.projectx.contracts.entity.TripPeriod;
import ru.projectx.contracts.support.Tasks;

class RequestIssuanceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final RequestReference REQUEST =
            new RequestReference(Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY);
    private static final TripPeriod PERIOD = new TripPeriod(Tasks.FROM, Tasks.TO);

    private final Contract contract = Contract.open(
            new ContractDraft("DKM-2026-000001", REQUEST, Tasks.SPOUSE, Tasks.INSURED, PERIOD, new BigDecimal("34000.00")),
            NOW);
    private final LmPolicy policy = LmPolicy.open(new LmPolicyDraft(REQUEST, Tasks.CHILD, Tasks.INSURED, PERIOD), NOW);
    private final RequestIssuance issuance = new RequestIssuance(List.of(contract), List.of(policy));

    @Test
    void activeRecords_areFoundByParticipantWithTheirKind() {
        assertThat(issuance.of(Tasks.SPOUSE)).contains(new ActiveIssuance(IssuanceKind.KM_CONTRACT, contract));
        assertThat(issuance.of(Tasks.CHILD)).contains(new ActiveIssuance(IssuanceKind.LM_POLICY, policy));
        assertThat(issuance.of(Tasks.INSURED)).isEmpty();
    }

    @Test
    void typedLookups_returnOnlyRecordsOfTheirKind() {
        assertThat(issuance.contractOf(Tasks.SPOUSE)).contains(contract);
        assertThat(issuance.contractOf(Tasks.CHILD)).isEmpty();
        assertThat(issuance.policyOf(Tasks.CHILD)).contains(policy);
        assertThat(issuance.policyOf(Tasks.SPOUSE)).isEmpty();
    }

    @Test
    void leftovers_areRecordsOfParticipantsNotForgotten() {
        issuance.forget(Tasks.SPOUSE);

        assertThat(issuance.leftovers()).containsExactly(policy);
        assertThat(issuance.of(Tasks.SPOUSE)).isEmpty();
    }
}
