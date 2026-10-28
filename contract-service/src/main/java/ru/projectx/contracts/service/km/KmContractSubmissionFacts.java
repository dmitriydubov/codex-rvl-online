package ru.projectx.contracts.service.km;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractAcceptance;
import ru.projectx.contracts.entity.party.ContractPerson;
import ru.projectx.contracts.entity.party.PersonRole;
import ru.projectx.contracts.entity.party.PersonSnapshot;
import ru.projectx.contracts.repository.issuance.ContractAcceptanceRepository;
import ru.projectx.contracts.repository.issuance.ContractPersonRepository;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.service.usk.request.UskRequestFacts;

@Component
public class KmContractSubmissionFacts implements UskRequestFacts<Contract, SubmissionFacts> {

    private final ContractPersonRepository persons;
    private final ContractAcceptanceRepository acceptances;
    private final InsuredCertificates certificates;

    public KmContractSubmissionFacts(ContractPersonRepository persons, ContractAcceptanceRepository acceptances,
            InsuredCertificates certificates) {
        this.persons = persons;
        this.acceptances = acceptances;
        this.certificates = certificates;
    }

    @Override
    public SubmissionFacts gather(Contract contract, ExternalIdentifiers identifiers) {
        List<ContractPerson> parties = persons.findByContractIdOrderByRole(contract.id());
        return new SubmissionFacts(identifiers, acceptedAt(contract), party(parties, PersonRole.POLICYHOLDER, contract),
                party(parties, PersonRole.INSURED, contract), certificates.of(contract));
    }

    private Instant acceptedAt(Contract contract) {
        return acceptances.findByContractId(contract.id())
                .map(ContractAcceptance::offer)
                .orElseThrow(() -> new IllegalStateException("у договора КМ " + contract.id() + " нет записи акцепта"))
                .acceptedAt();
    }

    private static PersonSnapshot party(List<ContractPerson> parties, PersonRole role, Contract contract) {
        return parties.stream()
                .filter(party -> party.role() == role)
                .findFirst()
                .map(ContractPerson::person)
                .orElseThrow(() -> new IllegalStateException(
                        "у договора КМ " + contract.id() + " нет снимка стороны " + role));
    }
}
