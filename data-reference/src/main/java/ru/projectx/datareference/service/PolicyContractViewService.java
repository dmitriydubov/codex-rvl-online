package ru.projectx.datareference.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.dto.PolicyContractView;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.PolicyContractViewRepository;

/**
 * Полис для сервиса Договоров, выбранный из базы сразу проекцией {@link PolicyContractView}: персональные
 * данные застрахованного не попадают ни в ответ, ни в память сервиса. Кэша нет: полис — не справочник,
 * и сервис Договоров читает его в момент выпуска, когда важна свежесть.
 */
@Service
public class PolicyContractViewService implements PolicyContractViews {

    private final PolicyContractViewRepository policies;

    public PolicyContractViewService(PolicyContractViewRepository policies) {
        this.policies = policies;
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyContractView byId(UUID id) {
        return policies.findContractViewById(id, PolicyContractView.class)
                .orElseThrow(() -> ReferenceNotFoundException.policy(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyContractView byExternalId(UUID externalId) {
        return policies.findContractViewByExternalId(externalId, PolicyContractView.class)
                .orElseThrow(() -> ReferenceNotFoundException.policyByExternalId(externalId));
    }
}
