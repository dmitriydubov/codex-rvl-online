package ru.projectx.datareference.service.policy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.dto.PolicyContractView;
import ru.projectx.datareference.exception.MissingReference;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.policy.PolicyContractRow;
import ru.projectx.datareference.repository.policy.PolicyContractViewRepository;
import ru.projectx.datareference.service.rest.RestReadTransaction;

@Service
@RestReadTransaction
public class PolicyContractViewService implements PolicyContractViews {

    private final PolicyContractViewRepository policies;

    public PolicyContractViewService(PolicyContractViewRepository policies) {
        this.policies = policies;
    }

    @Override
    public PolicyContractView byId(UUID id) {
        return view(id, policies::findContractViewById, MissingReference.POLICY::notFound);
    }

    @Override
    public PolicyContractView byExternalId(UUID externalId) {
        return view(externalId, policies::findContractViewByExternalId, MissingReference.POLICY_BY_EXTERNAL_ID::notFound);
    }

    private static PolicyContractView view(
            UUID key,
            Function<UUID, Optional<PolicyContractRow>> find,
            Function<UUID, ReferenceNotFoundException> notFound) {

        return find.apply(key)
                .map(row -> new PolicyContractView(
                        row.id(), row.externalId(), row.number(), row.beginDate(), row.endDate(), row.archive()))
                .orElseThrow(() -> notFound.apply(key));
    }
}
