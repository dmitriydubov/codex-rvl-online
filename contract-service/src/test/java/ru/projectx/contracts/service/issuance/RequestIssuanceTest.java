package ru.projectx.contracts.service.issuance;

import ru.projectx.contracts.entity.issuance.DocumentKind;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyDraft;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class RequestIssuanceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");

    private final Contract contract = Contract.open(
            new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE, new BigDecimal("34000.00")), NOW);
    private final LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
    private final RequestIssuance issuance = new RequestIssuance(List.of(contract), List.of(policy));

    @Test
    void activeRecords_areFoundByParticipantWithTheirKind() {
        assertThat(issuance.of(Tasks.SPOUSE)).contains(new ActiveIssuance(DocumentKind.KM_CONTRACT, "договор КМ", contract));
        assertThat(issuance.of(Tasks.CHILD)).contains(new ActiveIssuance(DocumentKind.LM_POLICY, "полис ЛМ", policy));
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

        assertThat(issuance.leftovers()).containsExactly(new ActiveIssuance(DocumentKind.LM_POLICY, "полис ЛМ", policy));
        assertThat(issuance.of(Tasks.SPOUSE)).isEmpty();
    }

    @Test
    void issuedRecord_isPreferredToAnOpenOneOfTheSameParticipantWhicheverComesFirst() {
        LmPolicy issued = Issuances.issuedPolicy("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39", Tasks.CHILD);

        assertThat(new RequestIssuance(List.of(), List.of(issued, policy)).policyOf(Tasks.CHILD)).contains(issued);
        assertThat(new RequestIssuance(List.of(), List.of(policy, issued)).policyOf(Tasks.CHILD)).contains(issued);
        assertThat(new RequestIssuance(List.of(), List.of(policy, issued)).leftovers())
                .containsExactly(new ActiveIssuance(DocumentKind.LM_POLICY, "полис ЛМ", issued));
    }

    @Test
    void issuedPolicy_isPreferredToAnOpenContractOfTheSameParticipant() {
        Contract childContract = Issuances.contract("3f1a2b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b", Tasks.CHILD,
                "DKM-2026-000002");
        LmPolicy issued = Issuances.issuedPolicy("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39", Tasks.CHILD);

        RequestIssuance mixed = new RequestIssuance(List.of(childContract), List.of(issued));

        assertThat(mixed.policyOf(Tasks.CHILD)).contains(issued);
        assertThat(mixed.contractOf(Tasks.CHILD)).isEmpty();
    }

    @Test
    void firstOfTwoIssuedRecords_isKept() {
        LmPolicy issued = Issuances.issuedPolicy("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39", Tasks.CHILD);
        LmPolicy another = Issuances.issuedPolicy("9f3e1c28-6b55-4da1-93f4-82a1d7b56e40", Tasks.CHILD);

        assertThat(new RequestIssuance(List.of(), List.of(issued, another)).policyOf(Tasks.CHILD)).contains(issued);
    }
}
