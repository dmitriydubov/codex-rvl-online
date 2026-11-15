package ru.projectx.datareference.service.policy;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.MissingReference;
import ru.projectx.datareference.repository.policy.PolicyRepository;

@Component
public class PolicyLookup {

    private final PolicyRepository policies;

    public PolicyLookup(PolicyRepository policies) {
        this.policies = policies;
    }

    public Policy byId(UUID id) {
        return policies.findById(id).orElseThrow(() -> MissingReference.POLICY.notFound(id));
    }

    public Policy byExternalId(UUID externalId) {
        return policies.findByExternalId(externalId)
                .orElseThrow(() -> MissingReference.POLICY_BY_EXTERNAL_ID.notFound(externalId));
    }

    public Policy byNumber(String number, UUID contractExternalId) {
        List<Policy> found = policies.findAllByNumberAndExternalContractId(number, contractExternalId);
        if (found.size() > 1) {
            throw AmbiguousReferenceException.policy(found.stream().map(Policy::id).toList());
        }
        return found.stream().findFirst()
                .orElseThrow(() -> MissingReference.POLICY_BY_NUMBER.notFound(contractExternalId));
    }
}
