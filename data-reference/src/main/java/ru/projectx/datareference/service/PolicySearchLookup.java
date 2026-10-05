package ru.projectx.datareference.service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.dto.PolicySearchResponse;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.repository.PolicyScope;

/**
 * Поиск полисов договора в базе для подсказки «найти сотрудника» — в одной транзакции REST-чтения с коротким
 * лимитом времени.
 *
 * <p>Сюда приходит уже проверенный вход: нормализованная строка поиска и предел выдачи. Проверяет его
 * {@link PolicySearchService} до вызова, и только он вызывает этот класс; поэтому класс не виден за пределами
 * пакета. Проверка идёт до вызова, потому что транзакция берёт соединение с базой в момент открытия.
 * Иначе при недоступной базе запрос с неверным параметром получил бы 503, и вызывающий повторял бы запрос,
 * который не пройдёт никогда.
 *
 * <p>Договор должен быть известен и не в архиве, иначе ответ «не найдено». Если в строке есть цифры, ищется
 * номер. Иначе строка режется на слова, и каждое слово должно быть началом фамилии, имени или отчества.
 * Отбор, порядок и предел выдачи делает один запрос в базе
 * ({@link ru.projectx.datareference.repository.PolicySearchRepository}).
 */
@Service
class PolicySearchLookup {

    private static final Pattern DIGIT = Pattern.compile("\\d");
    /** Слова в нормализованной строке поиска разделены ровно одним таким пробелом. */
    static final String WORD_SEPARATOR = " ";

    private final PolicyRepository policies;
    private final InsuranceContractRepository insuranceContracts;
    private final ReferenceCalendar calendar;

    PolicySearchLookup(
            PolicyRepository policies, InsuranceContractRepository insuranceContracts, ReferenceCalendar calendar) {

        this.policies = policies;
        this.insuranceContracts = insuranceContracts;
        this.calendar = calendar;
    }

    /** Полисы договора по нормализованной строке: слова разделены одним пробелом, буквы в нижнем регистре. */
    @RestReadTransaction
    public PolicySearchResponse search(UUID contractExternalId, String query, int limit) {
        insuranceContracts.findByExternalIdAndArchiveFalse(contractExternalId)
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContractByExternalId(contractExternalId));
        PolicyScope scope = new PolicyScope(contractExternalId, calendar.today(), limit);

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
