package ru.projectx.contracts.service;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractDraft;
import ru.projectx.contracts.entity.ContractTerms;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.repository.ContractRepository;

@Component
public class KmContractIssuance extends RecordIssuance<Contract> {

    private final ContractRepository contracts;
    private final ContractNumbers numbers;

    public KmContractIssuance(ContractRepository contracts, ContractNumbers numbers, PartySnapshotStore parties) {
        super(parties);
        this.contracts = contracts;
        this.numbers = numbers;
    }

    @Override
    protected List<Party> parties(Issue issue) {
        return List.of(new Party(PersonRole.POLICYHOLDER, PersonSnapshots.of(issue.task().insured())),
                new Party(PersonRole.INSURED, PersonSnapshots.of(issue.participant())));
    }

    @Override
    protected Contract open(Issue issue, Instant now) {
        ContractDraft draft = new ContractDraft(numbers.next(now), issue.request(), issue.participant().id(),
                issue.task().insured().id(), issue.period(), issue.participant().amountDue());
        return contracts.save(Contract.open(draft, now));
    }

    @Override
    protected PersonOwner owner(Contract contract) {
        return PersonOwner.contract(contract.id());
    }

    @Override
    protected void reviseTerms(Contract contract, Issue issue, Instant now) {
        contract.revise(new ContractTerms(issue.period(), issue.participant().amountDue()), now);
    }
}
