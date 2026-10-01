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
 * R-04 до базы: нормализация строки, выбор режима (номер или ФИО), шаблоны, предел и отказы.
 * Отбор, ранжирование и предел в SQL — в PolicyApiTest на настоящем PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
class PolicySearchServiceTest {

    private static final UUID CONTRACT = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    /** 2026-03-14 09:15 UTC — в Москве тот же день. */
    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);
    private static final PolicyScope DEFAULT_SCOPE = new PolicyScope(CONTRACT, TODAY, 10);

    @Mock
    private PolicyRepository policies;

    @Mock
    private InsuranceContractRepository insuranceContracts;

    @Mock
    private InsuranceContract contract;

    private PolicySearchService service;

    @BeforeEach
    void setUp() {
        var api = TestApiProperties.defaults();
        service = new PolicySearchService(policies, insuranceContracts,
                api, new ReferenceCalendar(Clock.fixed(NOW, ZoneOffset.UTC), api));
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

    private void knownContract() {
        when(insuranceContracts.findByExternalIdAndArchiveFalse(CONTRACT)).thenReturn(Optional.of(contract));
    }

    private static Policy policy(String lastName) {
        var details = new Policy.Details(CONTRACT, "П-0001", lastName, "Иван", null, LocalDate.of(1984, 3, 11),
                null, null, TODAY.minusMonths(1), null, true, false);
        return Policy.create(UUID.randomUUID(), new Policy.Snapshot(details, List.of(), List.of()), NOW);
    }
}
