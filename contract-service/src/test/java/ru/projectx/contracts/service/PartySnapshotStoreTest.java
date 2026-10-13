package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.entity.ContractPerson;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.repository.ContractPersonRepository;
import ru.projectx.contracts.support.Tasks;

class PartySnapshotStoreTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID CONTRACT = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
    private static final UUID POLICY = UUID.fromString("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39");

    private final ContractPersonRepository persons = mock();
    private final PartySnapshotStore store = new PartySnapshotStore(persons);
    private final List<Party> parties = List.of(
            new Party(PersonRole.POLICYHOLDER, PersonSnapshots.of(Tasks.insured("0.00"))),
            new Party(PersonRole.INSURED, PersonSnapshots.of(Tasks.spouse("34000.00"))));

    @Test
    void store_savesOnePersonPerPartyWithOwnerAndRole() {
        store.store(PersonOwner.contract(CONTRACT), parties, NOW);

        ArgumentCaptor<List<ContractPerson>> saved = ArgumentCaptor.captor();
        verify(persons).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(ContractPerson::role)
                .containsExactly(PersonRole.POLICYHOLDER, PersonRole.INSURED);
        assertThat(saved.getValue()).extracting(ContractPerson::person)
                .containsExactly(parties.get(0).person(), parties.get(1).person());
        assertThat(saved.getValue()).allSatisfy(person ->
                assertThat(person).hasFieldOrPropertyWithValue("contractId", CONTRACT));
    }

    @Test
    void differ_isFalseForTheSameSnapshotAndTrueForAnotherOne() {
        given(persons.findByContractIdOrderByRole(CONTRACT)).willReturn(stored(PersonOwner.contract(CONTRACT)));
        List<Party> changed = List.of(parties.get(0),
                new Party(PersonRole.INSURED, PersonSnapshots.of(Tasks.child("0.00"))));

        assertThat(store.differ(PersonOwner.contract(CONTRACT), parties)).isFalse();
        assertThat(store.differ(PersonOwner.contract(CONTRACT), changed)).isTrue();
        verify(persons, never()).deleteAll(anyList());
    }

    @Test
    void replaceIfChanged_keepsStoredPersonsWhenSnapshotIsTheSame() {
        given(persons.findByContractIdOrderByRole(CONTRACT)).willReturn(stored(PersonOwner.contract(CONTRACT)));

        assertThat(store.replaceIfChanged(PersonOwner.contract(CONTRACT), parties, NOW)).isFalse();
        verify(persons, never()).deleteAll(anyList());
        verify(persons, never()).saveAll(anyList());
    }

    @Test
    void replaceIfChanged_replacesStoredPersonsWhenSnapshotDiffers() {
        List<ContractPerson> stored = stored(PersonOwner.contract(CONTRACT));
        given(persons.findByContractIdOrderByRole(CONTRACT)).willReturn(stored);
        List<Party> changed = List.of(parties.get(0),
                new Party(PersonRole.INSURED, PersonSnapshots.of(Tasks.relative(1, Tasks.SPOUSE, "0.00", "CHILD"))));

        assertThat(store.replaceIfChanged(PersonOwner.contract(CONTRACT), changed, NOW)).isTrue();

        verify(persons).deleteAll(stored);
        verify(persons).flush();
        ArgumentCaptor<List<ContractPerson>> saved = ArgumentCaptor.captor();
        verify(persons).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(ContractPerson::person).containsExactly(
                changed.get(0).person(), changed.get(1).person());
    }

    @Test
    void replaceIfChanged_readsPolicyPersonsForPolicyOwner() {
        List<Party> insuredOnly = List.of(parties.get(1));
        given(persons.findByLmPolicyIdOrderByRole(POLICY)).willReturn(List.of(
                ContractPerson.snapshot(PersonOwner.lmPolicy(POLICY), PersonRole.INSURED, parties.get(1).person(), NOW)));

        assertThat(store.replaceIfChanged(PersonOwner.lmPolicy(POLICY), insuredOnly, NOW)).isFalse();
        verify(persons, never()).findByContractIdOrderByRole(POLICY);
    }

    private List<ContractPerson> stored(PersonOwner owner) {
        return parties.stream()
                .map(party -> ContractPerson.snapshot(owner, party.role(), party.person(), NOW))
                .toList();
    }
}
