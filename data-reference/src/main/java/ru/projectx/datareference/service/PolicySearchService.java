package ru.projectx.datareference.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.dto.PolicySearchResponse;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.repository.PolicyScope;

/**
 * Поиск полисов договора по части ФИО или номера (R-04): подсказка для поля «найти
 * сотрудника». Ищется среди действующих полисов договора с программами РВЛ —
 * базовая логика A, она в запросе репозитория.
 *
 * <p>Правила из ТЗ на API: строка нормализуется (обрезка, схлопывание пробелов,
 * нижний регистр, {@code ё → е}); есть цифры — ищем по номеру, иначе режем на токены,
 * и каждый токен должен быть префиксом фамилии, имени или отчества — так
 * «иван петр» находит и Петрова Ивана, и Иванова Петра. Ранжирование — точный номер,
 * затем совпали фамилия и имя, затем фамилия, затем остальное; внутри группы — по фамилии
 * и имени. Отбор, ранжирование и предел выдачи делает один запрос в базе
 * ({@link ru.projectx.datareference.repository.PolicySearchRepository}); здесь — правила
 * строки и пределов.
 *
 * <p>Строка поиска — это фамилии. Она не попадает ни в журнал, ни в текст ошибки:
 * отказ называет правило, а не значение.
 */
@Service
public class PolicySearchService implements PolicySearchReads {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final String TOKEN_SEPARATOR = " ";

    private final PolicyRepository policies;
    private final InsuranceContractRepository insuranceContracts;
    private final ReferenceApiProperties api;
    private final ReferenceCalendar calendar;

    public PolicySearchService(
            PolicyRepository policies,
            InsuranceContractRepository insuranceContracts,
            ReferenceApiProperties api,
            ReferenceCalendar calendar) {

        this.policies = policies;
        this.insuranceContracts = insuranceContracts;
        this.api = api;
        this.calendar = calendar;
    }

    @Override
    @RestReadTransaction
    public PolicySearchResponse search(UUID contractExternalId, String rawQuery, @Nullable Integer requestedLimit) {
        int limit = ResultLimits.resolve(requestedLimit, api.search().defaultLimit(), api.search().maxLimit());
        String query = normalize(rawQuery);
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
                Arrays.stream(names.split(TOKEN_SEPARATOR)).map(LikePatterns::prefix).toList());
    }

    private String normalize(String raw) {
        String query = WHITESPACE.matcher(raw.trim()).replaceAll(TOKEN_SEPARATOR)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е');
        if (query.length() < api.search().minQueryLength()) {
            throw new InvalidRequestException("query", "не короче " + api.search().minQueryLength() + " символов");
        }
        return query;
    }
}
