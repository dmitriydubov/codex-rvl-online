package ru.projectx.contracts.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.contract.ContractTerms;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.entity.party.PersonRole;
import ru.projectx.contracts.repository.ContractRepository;

@Component
public class KmContractIssuance extends RecordIssuance<Contract> {

    static final String KIND = "договор КМ";
    static final String ACCEPTED = "акцептован";
    static final String PAID = "оплачен";

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
    protected Optional<String> frozenBecause(Contract contract) {
        if (PaidContracts.failedAfterPayment(contract)) {
            return Optional.of(PAID);
        }
        return Optional.of(ACCEPTED).filter(reason -> contract.status().offerAccepted());
    }

    @Override
    protected boolean keepsTerms(Contract contract, Issue issue, UUID program) {
        return super.keepsTerms(contract, issue, program)
                && contract.premiumAmount().compareTo(issue.participant().amountDue()) == 0;
    }

    @Override
    protected boolean reviseTerms(Contract contract, Issue issue, UUID program) {
        boolean termsRevised = contract.revise(new ContractTerms(issue.period(), issue.participant().amountDue()));
        boolean programMoved = contract.moveProgram(program);
        contract.offerDraft();
        return termsRevised || programMoved;
    }
}
