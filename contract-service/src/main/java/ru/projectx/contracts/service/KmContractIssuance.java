package ru.projectx.contracts.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractDraft;
import ru.projectx.contracts.entity.ContractTerms;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.repository.ContractRepository;

@Component
public class KmContractIssuance extends RecordIssuance<Contract> {

    static final String KIND = "договор КМ";

    private final ContractRepository contracts;
    private final ContractNumbers numbers;

    public KmContractIssuance(ContractRepository contracts, ContractNumbers numbers, PartySnapshotStore parties) {
        super(parties);
        this.contracts = contracts;
        this.numbers = numbers;
    }

    @Override
    protected String kind() {
        return KIND;
    }

    @Override
    protected List<Party> parties(Issue issue) {
        return List.of(new Party(PersonRole.POLICYHOLDER, PersonSnapshots.of(issue.task().insured())),
                new Party(PersonRole.INSURED, PersonSnapshots.of(issue.participant())));
    }

    @Override
    protected Contract open(Issue issue, Instant now) {
        ContractDraft draft = new ContractDraft(numbers.next(now), issue.terms(), issue.participant().id(),
                issue.participant().amountDue());
        Contract contract = Contract.open(draft, now);
        contract.offerDraft();
        return contracts.save(contract);
    }

    @Override
    protected PersonOwner owner(Contract contract) {
        return PersonOwner.contract(contract.id());
    }

    @Override
    protected boolean submitted(Contract contract) {
        return false;
    }

    @Override
    protected boolean reviseTerms(Contract contract, Issue issue, UUID program) {
        boolean termsRevised = contract.revise(new ContractTerms(issue.period(), issue.participant().amountDue()));
        boolean programMoved = contract.moveProgram(program);
        contract.offerDraft();
        return termsRevised || programMoved;
    }
}
