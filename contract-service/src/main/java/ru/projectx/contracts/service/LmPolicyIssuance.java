package ru.projectx.contracts.service;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.repository.LmPolicyRepository;

@Component
public class LmPolicyIssuance extends RecordIssuance<LmPolicy> {

    private final LmPolicyRepository policies;

    public LmPolicyIssuance(LmPolicyRepository policies, PartySnapshotStore parties) {
        super(parties);
        this.policies = policies;
    }

    @Override
    protected List<Party> parties(Issue issue) {
        return List.of(new Party(PersonRole.INSURED, PersonSnapshots.of(issue.participant())));
    }

    @Override
    protected LmPolicy open(Issue issue, Instant now) {
        LmPolicyDraft draft = new LmPolicyDraft(issue.request(), issue.participant().id(),
                issue.task().insured().id(), issue.period());
        return policies.save(LmPolicy.open(draft, now));
    }

    @Override
    protected PersonOwner owner(LmPolicy policy) {
        return PersonOwner.lmPolicy(policy.id());
    }

    @Override
    protected boolean reviseTerms(LmPolicy policy, Issue issue) {
        return policy.movePeriod(issue.period());
    }
}
