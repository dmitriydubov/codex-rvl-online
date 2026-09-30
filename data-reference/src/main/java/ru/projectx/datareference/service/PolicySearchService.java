package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.dto.PolicySearchResponse;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.repository.PolicySpecifications;

/**
 * Поиск полисов договора по части ФИО или номера (R-04): подсказка для поля «найти
 * сотрудника». Ищется среди действующих полисов договора с программами РВЛ —
 * базовая логика A, она в запросе репозитория.
 *
 * <p>Правила из ТЗ на API: строка нормализуется (обрезка, схлопывание пробелов,
 * нижний регистр, {@code ё → е}); есть цифры — ищем по номеру, иначе режем на токены,
 * и каждый токен должен быть префиксом фамилии, имени или отчества — так
 * «иван петр» находит и Петрова Ивана, и Иванова Петра. Отбор — условиями
 * {@link PolicySpecifications} в базе; ранжирование — здесь: точный номер, затем
 * совпали фамилия и имя, затем фамилия, затем остальное; внутри группы — по фамилии
 * и имени, этот порядок задаёт сама выборка.
 *
 * <p>Строка поиска — это фамилии. Она не попадает ни в журнал, ни в текст ошибки:
 * отказ называет правило, а не значение.
 */
@Service
public class PolicySearchService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final int EXACT_NUMBER = 0;
    private static final int NUMBER_PREFIX = 1;
    private static final int NUMBER_SUBSTRING = 2;
    private static final int LAST_AND_FIRST_NAME = 1;
    private static final int LAST_NAME = 2;
    private static final int OTHER = 3;
    private static final Sort BY_NAME = Sort.by("lastName", "firstName");

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

    @Transactional(readOnly = true)
    public PolicySearchResponse search(UUID contractExternalId, String rawQuery, @Nullable Integer requestedLimit) {
        int limit = limitOf(requestedLimit);
        String query = normalize(rawQuery);
        insuranceContracts.findByExternalIdAndArchiveFalse(contractExternalId)
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContractByExternalId(contractExternalId));
        LocalDate today = calendar.today();

        List<Policy> ranked = DIGIT.matcher(query).find()
                ? byNumber(contractExternalId, query, today)
                : byNames(contractExternalId, query, today);
        return PolicySearchResponse.of(ranked.stream().limit(limit).toList());
    }

    private List<Policy> byNumber(UUID contract, String query, LocalDate today) {
        Specification<Policy> matching = Specification.allOf(
                PolicySpecifications.activeRvlPoliciesOf(contract, today),
                PolicySpecifications.numberContains("%" + escapeLike(query) + "%"));
        return policies.findAll(matching, BY_NAME).stream()
                .sorted(Comparator.comparingInt((Policy policy) -> numberRank(policy, query)))
                .toList();
    }

    private List<Policy> byNames(UUID contract, String query, LocalDate today) {
        String[] tokens = query.split(" ");
        Specification<Policy> matching = Specification.allOf(
                PolicySpecifications.activeRvlPoliciesOf(contract, today),
                PolicySpecifications.namesPrefixedBy(Arrays.stream(tokens).map(PolicySearchService::escapeLike).toList()));
        return policies.findAll(matching, BY_NAME).stream()
                .sorted(Comparator.comparingInt((Policy policy) -> nameRank(policy, tokens)))
                .toList();
    }

    private static int numberRank(Policy policy, String query) {
        String number = policy.number().toLowerCase(Locale.ROOT);
        if (number.equals(query)) {
            return EXACT_NUMBER;
        }
        return number.startsWith(query) ? NUMBER_PREFIX : NUMBER_SUBSTRING;
    }

    private static int nameRank(Policy policy, String[] tokens) {
        boolean lastName = prefixedByAny(policy.lastName(), tokens);
        boolean firstName = prefixedByAny(policy.firstName(), tokens);
        if (lastName && firstName) {
            return LAST_AND_FIRST_NAME;
        }
        return lastName ? LAST_NAME : OTHER;
    }

    private static boolean prefixedByAny(String value, String[] tokens) {
        String normalized = normalizeText(value);
        return Arrays.stream(tokens).anyMatch(normalized::startsWith);
    }

    private int limitOf(@Nullable Integer requested) {
        int limit = requested == null ? api.search().defaultLimit() : requested;
        if (limit < 1 || limit > api.search().maxLimit()) {
            throw new InvalidRequestException("limit", "от 1 до " + api.search().maxLimit());
        }
        return limit;
    }

    private String normalize(String raw) {
        String query = normalizeText(raw);
        if (query.length() < api.search().minQueryLength()) {
            throw new InvalidRequestException("query", "не короче " + api.search().minQueryLength() + " символов");
        }
        return query;
    }

    private static String normalizeText(String value) {
        return WHITESPACE.matcher(value.trim()).replaceAll(" ").toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    /** Символ экранирования в запросах репозитория — {@code !}; в самих ФИО и номерах его не бывает. */
    static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
