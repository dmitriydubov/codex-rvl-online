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
 * Поиск полисов договора по части ФИО или номера: подсказка для поля «найти сотрудника». Ищется среди
 * действующих полисов договора с программами восстановительного лечения; это условие стоит в запросе
 * репозитория.
 *
 * <p>Строка нормализуется: обрезаются пробелы по краям, подряд идущие пробелы схлопываются, буквы приводятся
 * к нижнему регистру, {@code ё} заменяется на {@code е}. Если в строке есть цифры, ищется номер. Иначе строка
 * режется на слова, и каждое слово должно быть началом фамилии, имени или отчества. Так «иван петр» находит
 * и Петрова Ивана, и Иванова Петра. Сначала идёт точный номер, затем совпавшие фамилия и имя, затем фамилия,
 * затем остальное; внутри группы — по фамилии и имени. Отбор, порядок и предел выдачи делает один запрос в
 * базе ({@link ru.projectx.datareference.repository.PolicySearchRepository}), а здесь проверяются строка и
 * пределы. Длина строки сверху проверяется первой, по строке как пришла (см. {@link SearchInputLimits}).
 *
 * <p>Строка поиска — это фамилии. Она не попадает ни в журнал, ни в текст ошибки: отказ называет правило,
 * а не значение.
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
        SearchInputLimits.requireQueryNotLongerThan(rawQuery, api.search().maxQueryLength());
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
