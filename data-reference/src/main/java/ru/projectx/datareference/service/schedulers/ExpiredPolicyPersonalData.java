package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.PersonalDataCutoff;
import ru.projectx.datareference.mapper.PolicyPersonalDataTerm;
import ru.projectx.datareference.repository.PolicyRepository;

/**
 * Персональные данные полисов, срок хранения которых истёк: стирание пачкой для прохода стирания и подсчёт для
 * метрики. Граница считается по тому же сроку, что у разбора полиса, на момент по часам сервиса, поэтому полис,
 * пришедший уже после срока, и полис, срок которого истёк в базе, обезличиваются по одному правилу.
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

    /**
     * Стирает ФИО, дату рождения и почту не больше чем у {@code limit} полисов за пределом срока хранения. Транзакцию
     * открывает вызывающий.
     *
     * @return у скольких полисов стёрто
     */
    public int erase(int limit) {
        Instant now = clock.instant();
        PersonalDataCutoff cutoff = term.cutoffAt(now);
        return policies.erasePersonalDataBeyond(cutoff.endedBefore(), cutoff.archivedBy(), now, limit);
    }

    /** Сколько полисов на момент {@code now} за пределом срока хранения, а их данные ещё не стёрты. */
    public long countAt(Instant now) {
        PersonalDataCutoff cutoff = term.cutoffAt(now);
        return policies.countPersonalDataBeyond(cutoff.endedBefore(), cutoff.archivedBy());
    }
}
