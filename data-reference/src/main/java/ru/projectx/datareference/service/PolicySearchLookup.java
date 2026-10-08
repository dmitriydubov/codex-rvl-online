package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.dto.PolicySearchResponse;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.repository.PolicyScope;

/**
 * Ищет полисы действующего договора для подсказки «найти сотрудника» по входу, уже проверенному
 * {@link PolicySearchService}: при недоступной базе неверный запрос не должен получать 503. Строка с цифрами
 * ищется как номер, иначе каждое слово должно начинать фамилию, имя или отчество.
 */
@Service
class PolicySearchLookup {

    private static final Pattern DIGIT = Pattern.compile("\\d");
    /** Слова в нормализованной строке поиска разделены ровно одним пробелом. */
    static final String WORD_SEPARATOR = " ";

    private final PolicyRepository policies;
    private final ActiveInsuranceContracts activeContracts;
    private final ReferenceCalendar calendar;

    PolicySearchLookup(
            PolicyRepository policies, ActiveInsuranceContracts activeContracts, ReferenceCalendar calendar) {

        this.policies = policies;
        this.activeContracts = activeContracts;
        this.calendar = calendar;
    }

    /** Строка уже нормализована: слова разделены одним пробелом, буквы в нижнем регистре. */
    @RestReadTransaction
    public PolicySearchResponse search(UUID contractExternalId, String query, int limit) {
        LocalDate today = calendar.today();
        activeContracts.requireActive(contractExternalId, today);
        PolicyScope scope = new PolicyScope(contractExternalId, today, limit);

        List<Policy> ranked = DIGIT.matcher(query).find() ? byNumber(scope, query) : byNames(scope, query);
        return PolicySearchResponse.of(ranked);
    }

    private List<Policy> byNumber(PolicyScope scope, String number) {
        return policies.findRankedByNumber(scope, number, LikePatterns.prefix(number), LikePatterns.substring(number));
    }

    private List<Policy> byNames(PolicyScope scope, String names) {
        return policies.findRankedByNames(scope,
                Arrays.stream(names.split(WORD_SEPARATOR)).map(LikePatterns::prefix).toList());
    }
}
