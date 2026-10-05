package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.dto.PolicySearchResponse;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.repository.PolicyScope;
import ru.projectx.datareference.support.TestApiProperties;

/**
 * Поиск полисов договора до базы: нормализация строки, выбор режима (номер или ФИО), шаблоны, пределы и
 * отказы. Отбор, ранжирование и предел выдачи в SQL проверяет PolicyApiTest на настоящем PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
class PolicySearchServiceTest {

    private static final UUID CONTRACT = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    /** 2026-03-14 09:15 UTC — в Москве тот же день. */
    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);
    private static final PolicyScope DEFAULT_SCOPE = new PolicyScope(CONTRACT, TODAY, 10);
    private static final int MAX_QUERY_LENGTH = 100;
    /** Предел, который не совпадает ни с одним другим пределом в настройках. */
    private static final int CONFIGURED_MAX_QUERY_LENGTH = 7;
    /** Эмодзи U+1F600: один символ, но в строке Java занимает две единицы UTF-16 (суррогатную пару). */
    private static final String OUTSIDE_BMP = Character.toString(0x1F600);

    @Mock
    private PolicyRepository policies;

    @Mock
    private InsuranceContractRepository insuranceContracts;

    @Mock
    private InsuranceContract contract;

    private PolicySearchService service;

    @BeforeEach
    void setUp() {
        service = serviceWith(TestApiProperties.defaults());
    }

    /** Обрезка, схлопывание пробелов, нижний регистр и ё → е — до того, как строка станет шаблоном. */
    @ParameterizedTest
    @ValueSource(strings = {"иван петр", "  Иван    ПЁТР  ", "ИВАН\tпётр"})
    void nameTokens_areNormalizedPrefixes_inOneQuery(String query) {
        knownContract();
        when(policies.findRankedByNames(DEFAULT_SCOPE, List.of("иван%", "петр%"))).thenReturn(List.of(policy("Петров")));

        PolicySearchResponse response = service.search(CONTRACT, query, null);

        assertThat(response.items()).extracting(PolicySearchResponse.Item::lastName).containsExactly("Петров");
    }

    @Test
    void patternCharactersInName_areSearchedLiterally() {
        knownContract();
        when(policies.findRankedByNames(DEFAULT_SCOPE, List.of("а!_б%"))).thenReturn(List.of());

        assertThat(service.search(CONTRACT, "а_б", null).items()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "26 KM R/016039/1  | 26 km r/016039/1",
            "  П-0001          | п-0001",
            "5%                | 5%"})
    void queryWithDigit_isNumber_exactPrefixAndSubstring(String query, String number) {
        knownContract();
        when(policies.findRankedByNumber(DEFAULT_SCOPE, number, LikePatterns.prefix(number),
                LikePatterns.substring(number))).thenReturn(List.of(policy("Орлов")));

        assertThat(service.search(CONTRACT, query, null).items()).hasSize(1);
        verify(policies, never()).findRankedByNames(any(), anyList());
    }

    @Test
    void requestedLimit_goesToQuery() {
        knownContract();
        when(policies.findRankedByNames(new PolicyScope(CONTRACT, TODAY, 3), List.of("ив%"))).thenReturn(List.of());

        assertThat(service.search(CONTRACT, "ив", 3).items()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "и", "  и  ", "ё"})
    void queryShorterThanMinimum_isValidationError_namingRuleNotValue(String query) {
        assertThatThrownBy(() -> service.search(CONTRACT, query, null))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("query");
                    assertThat(invalid.getMessage()).isEqualTo("не короче 2 символов");
                });
        verifyNoInteractions(policies, insuranceContracts);
    }

    /** Длина считается по строке как пришла, вместе с пробелами по краям: предел защищает от объёма. */
    @Test
    void queryAtMaximumLength_isSearched() {
        knownContract();
        String query = "ж".repeat(MAX_QUERY_LENGTH);
        when(policies.findRankedByNames(DEFAULT_SCOPE, List.of(LikePatterns.prefix(query)))).thenReturn(List.of());

        assertThat(service.search(CONTRACT, query, null).items()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ж", " "})
    void queryLongerThanMaximum_asReceived_isValidationError_namingRuleNotValue(String filler) {
        String query = " " + filler.repeat(MAX_QUERY_LENGTH - 1) + " ";

        assertThatThrownBy(() -> service.search(CONTRACT, query, null))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("query");
                    assertThat(invalid.getMessage()).isEqualTo("не длиннее 100 символов");
                });
        verifyNoInteractions(policies, insuranceContracts);
    }

    /**
     * Длина считается в символах, а не в единицах UTF-16. Здесь строка ровно в предел символов, но последний
     * символ лежит вне основной плоскости Юникода и занимает две единицы, так что по {@code length()} строка
     * на одну единицу длиннее предела. Она всё равно ищется.
     */
    @Test
    void queryAtMaximumLength_withCharacterOutsideBmp_isSearched() {
        knownContract();
        String query = "ж".repeat(MAX_QUERY_LENGTH - 1) + OUTSIDE_BMP;
        assertThat(query).as("единиц UTF-16 в строке").hasSize(MAX_QUERY_LENGTH + 1);
        when(policies.findRankedByNames(DEFAULT_SCOPE, List.of(LikePatterns.prefix(query))))
                .thenReturn(List.of(policy("Петров")));

        assertThat(service.search(CONTRACT, query, null).items())
                .extracting(PolicySearchResponse.Item::lastName).containsExactly("Петров");
    }

    /** Длина проверяется первой: строка сверх предела отклоняется по полю query, даже когда неверен и limit. */
    @Test
    void queryLongerThanMaximum_withLimitOutOfBounds_isValidationErrorOnQuery() {
        String query = "ж".repeat(MAX_QUERY_LENGTH + 1);

        assertThatThrownBy(() -> service.search(CONTRACT, query, 0))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo("query"));
        verifyNoInteractions(policies, insuranceContracts);
    }

    /**
     * Предел длины строки — своя настройка поиска полисов. В умолчаниях он равен пределу строки МКБ-10, поэтому
     * здесь он другой: строка ровно в него ищется, на букву длиннее — нет.
     */
    @Test
    void queryLength_isLimitedByPolicySearchSetting() {
        PolicySearchService configured =
                serviceWith(TestApiProperties.withPolicySearchMaxQueryLength(CONFIGURED_MAX_QUERY_LENGTH));
        knownContract();
        String query = "ж".repeat(CONFIGURED_MAX_QUERY_LENGTH);
        when(policies.findRankedByNames(DEFAULT_SCOPE, List.of(LikePatterns.prefix(query))))
                .thenReturn(List.of(policy("Петров")));

        assertThat(configured.search(CONTRACT, query, null).items()).hasSize(1);
        assertThatThrownBy(() -> configured.search(CONTRACT, query + "ж", null))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("query");
                    assertThat(invalid.getMessage()).isEqualTo("не длиннее 7 символов");
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 51})
    void limitOutOfBounds_isValidationError_withoutQuery(int limit) {
        assertThatThrownBy(() -> service.search(CONTRACT, "иванов", limit))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo("limit"));
        verifyNoInteractions(policies, insuranceContracts);
    }

    @Test
    void unknownOrArchivedContract_isNotFound_withoutSearch() {
        when(insuranceContracts.findByExternalIdAndArchiveFalse(CONTRACT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.search(CONTRACT, "иванов", null))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.code()).isEqualTo("INSURANCE_CONTRACT_NOT_FOUND"));
        verify(policies, never()).findRankedByNames(any(), anyList());
        verify(policies, never()).findRankedByNumber(any(), anyString(), anyString(), anyString());
    }

    private PolicySearchService serviceWith(ReferenceApiProperties api) {
        return new PolicySearchService(policies, insuranceContracts,
                api, new ReferenceCalendar(Clock.fixed(NOW, ZoneOffset.UTC), api));
    }

    private void knownContract() {
        when(insuranceContracts.findByExternalIdAndArchiveFalse(CONTRACT)).thenReturn(Optional.of(contract));
    }

    private static Policy policy(String lastName) {
        var details = new Policy.Details(CONTRACT, "П-0001", lastName, "Иван", null, LocalDate.of(1984, 3, 11),
                null, null, TODAY.minusMonths(1), null, true, false);
        return Policy.create(UUID.randomUUID(), new Policy.Snapshot(details, List.of(), List.of()), NOW);
    }
}
