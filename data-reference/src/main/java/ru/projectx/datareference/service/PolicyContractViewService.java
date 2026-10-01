package ru.projectx.datareference.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.dto.PolicyContractView;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.PolicyContractRow;
import ru.projectx.datareference.repository.PolicyContractViewRepository;

/**
 * Полис для сервиса Договоров, выбранный из базы сразу строкой без ПДн ({@link PolicyContractRow}):
 * персональные данные застрахованного не попадают ни в ответ, ни в память сервиса. Кэша нет: полис —
 * не справочник, и сервис Договоров читает его в момент выпуска, когда важна свежесть.
 *
 * <p>Транзакция {@code readOnly} — на весь класс: здесь только чтение.
 */
@Service
@Transactional(readOnly = true)
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

    /** Строка по ключу — в тело ответа; нет строки — отказ, который называет этот ключ. */
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
