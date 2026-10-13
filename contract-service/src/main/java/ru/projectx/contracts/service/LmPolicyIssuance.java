package ru.projectx.contracts.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.repository.LmPolicyRepository;

@Component
public class LmPolicyIssuance extends RecordIssuance<LmPolicy> {

    static final String KIND = "полис ЛМ";

    private final LmPolicyRepository policies;

    public LmPolicyIssuance(LmPolicyRepository policies, PartySnapshotStore parties) {
        super(parties);
        this.policies = policies;
    }

    @Override
    protected String kind() {
        return KIND;
    }

    @Override
    protected List<Party> parties(Issue issue) {
        return List.of(new Party(PersonRole.INSURED, PersonSnapshots.of(issue.participant())));
    }

    @Override
    protected LmPolicy open(Issue issue, Instant now) {
        return policies.save(LmPolicy.open(new LmPolicyDraft(issue.terms(), issue.participant().id()), now));
    }

    @Override
    protected PersonOwner owner(LmPolicy policy) {
        return PersonOwner.lmPolicy(policy.id());
    }

    @Override
    protected boolean submitted(LmPolicy policy) {
        return policy.submitted();
    }

    @Override
    protected boolean reviseTerms(LmPolicy policy, Issue issue, UUID program) {
        boolean periodMoved = policy.movePeriod(issue.period());
        boolean programMoved = policy.moveProgram(program);
        return periodMoved || programMoved;
    }
}
