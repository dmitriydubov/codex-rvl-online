package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractDraft;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.entity.RequestReference;
import ru.projectx.contracts.entity.TripPeriod;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.support.Tasks;

class KmContractIssuanceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");

    private final ContractRepository contracts = mock();
    private final ContractNumbers numbers = mock();
    private final PartySnapshotStore parties = mock();
    private final KmContractIssuance issuance = new KmContractIssuance(contracts, numbers, parties);
    private final ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"));

    @BeforeEach
    void repositoryReturnsSavedContract() {
        given(contracts.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(numbers.next(NOW)).willReturn("DKM-2026-000001");
    }

    @Test
    void newContract_getsNumberTripDatesPremiumPolicyholderAndTwoParties() {
        issuance.reconcile(new Issue(task, Tasks.spouse("34000.00")), Optional.empty(), NOW);

        Contract contract = savedContract();
        assertThat(contract.number()).isEqualTo("DKM-2026-000001");
        assertThat(contract.status()).isEqualTo(ContractStatus.NEW);
        assertThat(contract.insuredParticipantId()).isEqualTo(Tasks.SPOUSE);
        assertThat(contract.state().policyholderParticipantId()).isEqualTo(Tasks.INSURED);
        assertThat(contract.state().period()).isEqualTo(new TripPeriod(Tasks.FROM, Tasks.TO));
        assertThat(contract.premiumAmount()).isEqualByComparingTo("34000.00");
        assertThat(contract.state().request())
                .isEqualTo(new RequestReference(Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY));
        verify(parties).store(PersonOwner.contract(contract.id()), List.of(
                new Party(PersonRole.POLICYHOLDER, PersonSnapshots.of(Tasks.insured("0.00"))),
                new Party(PersonRole.INSURED, PersonSnapshots.of(Tasks.spouse("34000.00")))), NOW);
    }

    @Test
    void existingContract_isRevisedInsteadOfOpened() {
        Contract existing = existing("34000.00");
        given(parties.replaceIfChanged(eq(PersonOwner.contract(existing.id())), anyList(), eq(NOW))).willReturn(false);
        ContractTask moved = Tasks.task(Tasks.FROM.plusDays(1), Tasks.TO.plusDays(1),
                Tasks.insured("0.00"), Tasks.spouse("36000.00"));

        issuance.reconcile(new Issue(moved, Tasks.spouse("36000.00")), Optional.of(existing), NOW);

        verify(contracts, never()).save(any());
        verify(numbers, never()).next(any());
        assertThat(existing.premiumAmount()).isEqualByComparingTo("36000.00");
        assertThat(existing.state().period()).isEqualTo(new TripPeriod(Tasks.FROM.plusDays(1), Tasks.TO.plusDays(1)));
        assertThat(existing.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void existingContract_withSameTermsAndParties_keepsVersion() {
        Contract existing = existing("34000.00");
        given(parties.replaceIfChanged(eq(PersonOwner.contract(existing.id())), anyList(), eq(NOW))).willReturn(false);

        issuance.reconcile(new Issue(task, Tasks.spouse("34000.00")), Optional.of(existing), NOW);

        assertThat(existing.state().entityVersion()).isEqualTo(1);
    }

    @Test
    void existingContract_withChangedParties_bumpsVersion() {
        Contract existing = existing("34000.00");
        given(parties.replaceIfChanged(eq(PersonOwner.contract(existing.id())), anyList(), eq(NOW))).willReturn(true);

        issuance.reconcile(new Issue(task, Tasks.spouse("34000.00")), Optional.of(existing), NOW);

        assertThat(existing.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void invalidParticipant_opensContractInErrorWithoutParties() {
        ContractTask invalid = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.SPOUSE, "34000.00", null));

        issuance.reconcile(new Issue(invalid, invalid.participants().get(1)), Optional.empty(), NOW);

        Contract contract = savedContract();
        assertThat(contract.status()).isEqualTo(ContractStatus.ERROR);
        assertThat(contract.number()).isEqualTo("DKM-2026-000001");
        assertThat(contract.premiumAmount()).isEqualByComparingTo("34000.00");
        assertThat(contract.state().error()).contains(new IssueError("RELATION_TYPE_REQUIRED",
                "поле participants[1].personal_data.relation_type обязательно для родственника"));
        verify(parties, never()).store(any(), anyList(), any());
    }

    @Test
    void invalidParticipant_withExistingContract_marksItInError() {
        Contract existing = existing("34000.00");
        ContractTask invalid = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.SPOUSE, "34000.00", null));

        issuance.reconcile(new Issue(invalid, invalid.participants().get(1)), Optional.of(existing), NOW);

        assertThat(existing.status()).isEqualTo(ContractStatus.ERROR);
        assertThat(existing.state().error()).map(IssueError::code).contains("RELATION_TYPE_REQUIRED");
        verify(contracts, never()).save(any());
    }

    private Contract savedContract() {
        ArgumentCaptor<Contract> saved = ArgumentCaptor.captor();
        verify(contracts).save(saved.capture());
        return saved.getValue();
    }

    private static Contract existing(String premium) {
        return Contract.open(new ContractDraft("DKM-2026-000001",
                new RequestReference(Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY),
                Tasks.SPOUSE, Tasks.INSURED, new TripPeriod(Tasks.FROM, Tasks.TO), new BigDecimal(premium)), NOW);
    }
}
