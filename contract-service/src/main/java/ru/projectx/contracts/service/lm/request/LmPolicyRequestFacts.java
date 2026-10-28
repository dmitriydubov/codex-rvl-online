package ru.projectx.contracts.service.lm.request;

import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.party.PersonRole;
import ru.projectx.contracts.repository.issuance.ContractPersonRepository;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.service.usk.request.UskRequestFacts;

@Component
public class LmPolicyRequestFacts implements UskRequestFacts<LmPolicy, LmRequestFacts> {

    private final ContractPersonRepository persons;

    public LmPolicyRequestFacts(ContractPersonRepository persons) {
        this.persons = persons;
    }

    @Override
    public LmRequestFacts gather(LmPolicy policy, ExternalIdentifiers identifiers) {
        return new LmRequestFacts(persons.findByLmPolicyIdOrderByRole(policy.id()).stream()
                .filter(person -> person.role() == PersonRole.INSURED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("у полиса ЛМ " + policy.id() + " нет снимка застрахованного"))
                .person(), identifiers);
    }
}
