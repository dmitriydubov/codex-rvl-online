package ru.projectx.contracts.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.ContractPerson;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.repository.ContractPersonRepository;

@Component
public class PartySnapshotStore {

    private final ContractPersonRepository persons;

    public PartySnapshotStore(ContractPersonRepository persons) {
        this.persons = persons;
    }

    public void store(PersonOwner owner, List<Party> parties, Instant now) {
        persons.saveAll(parties.stream()
                .map(party -> ContractPerson.snapshot(owner, party.role(), party.person(), now))
                .toList());
    }

    public boolean replaceIfChanged(PersonOwner owner, List<Party> parties, Instant now) {
        List<Party> stored = stored(owner).stream()
                .map(person -> new Party(person.role(), person.person()))
                .toList();
        if (stored.equals(parties)) {
            return false;
        }
        persons.deleteAll(stored(owner));
        persons.flush();
        store(owner, parties, now);
        return true;
    }

    private List<ContractPerson> stored(PersonOwner owner) {
        if (owner.contractId() != null) {
            return persons.findByContractIdOrderByRole(owner.contractId());
        }
        return persons.findByLmPolicyIdOrderByRole(Objects.requireNonNull(owner.lmPolicyId()));
    }
}
