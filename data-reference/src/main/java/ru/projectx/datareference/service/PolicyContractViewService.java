package ru.projectx.datareference.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.dto.PolicyContractView;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.PolicyContractRow;
import ru.projectx.datareference.repository.PolicyContractViewRepository;

/**
 * Полис для сервиса Договоров, выбранный из базы строкой без персональных данных, чтобы они не попали ни в ответ,
 * ни в память сервиса. Кэша нет: Договоры читают полис в момент выпуска, и важна свежесть.
 */
@Service
@RestReadTransaction
public class PolicyContractViewService implements PolicyContractViews {

    private final PolicyContractViewRepository policies;

    public PolicyContractViewService(PolicyContractViewRepository policies) {
        this.policies = policies;
    }

    @Override
    public PolicyContractView byId(UUID id) {
        return view(id, policies::findContractViewById, ReferenceNotFoundException::policy);
    }

    @Override
    public PolicyContractView byExternalId(UUID externalId) {
        return view(externalId, policies::findContractViewByExternalId, ReferenceNotFoundException::policyByExternalId);
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
