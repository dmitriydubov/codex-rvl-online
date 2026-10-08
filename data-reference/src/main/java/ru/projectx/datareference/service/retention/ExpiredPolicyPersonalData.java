package ru.projectx.datareference.service.retention;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.PersonalDataCutoff;
import ru.projectx.datareference.mapper.PolicyPersonalDataTerm;
import ru.projectx.datareference.repository.PolicyRepository;

/**
 * Стирает пачкой и считает для метрики персональные данные полисов с истёкшим сроком хранения. Граница та же, что
 * у разбора полиса, поэтому полис, пришедший после срока, и полис, истёкший в базе, обезличиваются одинаково.
 */
@Component
public class ExpiredPolicyPersonalData {

    private final PolicyRepository policies;
    private final PolicyPersonalDataTerm term;
    private final Clock clock;

    public ExpiredPolicyPersonalData(PolicyRepository policies, PolicyPersonalDataTerm term, Clock clock) {
        this.policies = policies;
        this.term = term;
        this.clock = clock;
    }

    /** Стирает ФИО, дату рождения и почту не больше чем у {@code limit} полисов в транзакции вызывающего. */
    public int erase(int limit) {
        Instant now = clock.instant();
        PersonalDataCutoff cutoff = term.cutoffAt(now);
        return policies.erasePersonalDataBeyond(cutoff.endedBefore(), cutoff.archivedBy(), now, limit);
    }

    /** Считает только полисы, данные которых ещё не стёрты. */
    public long countAt(Instant now) {
        PersonalDataCutoff cutoff = term.cutoffAt(now);
        return policies.countPersonalDataBeyond(cutoff.endedBefore(), cutoff.archivedBy());
    }
}
