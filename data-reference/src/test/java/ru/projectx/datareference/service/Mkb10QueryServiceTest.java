package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import ru.projectx.datareference.dto.Mkb10Code;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.repository.Mkb10Repository;
import ru.projectx.datareference.repository.Mkb10Row;
import ru.projectx.datareference.support.TestApiProperties;

@ExtendWith(MockitoExtension.class)
class Mkb10QueryServiceTest {

    private static final Limit DEFAULT_LIMIT = Limit.of(20);
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_CODES = 100;
    private static final int CONFIGURED_MAX_QUERY_LENGTH = 5;
    private static final int CONFIGURED_MAX_CODES = 3;
    private static final String OUTSIDE_BMP = Character.toString(0x1F600);
    private static final Mkb10Row Q06_0 = new Mkb10Row(UUID.fromString("6b1d0000-0000-4000-8000-000000000002"),
            UUID.fromString("6b1d0000-0000-4000-8000-000000000012"), "Q06.0", "Амиелия", 3, "Q06", false);

    @Mock
    private Mkb10Repository mkb10;

    private Mkb10QueryService service;

    @BeforeEach
    void setUp() {
        service = new Mkb10QueryService(new Mkb10Lookup(mkb10), TestApiProperties.defaults());
    }

    @ParameterizedTest
    @ValueSource(strings = {"q0", "Q06", "  i2 ", "a1 текст"})
    void queryStartingWithLetterAndDigit_isCodePrefix_upperCased(String query) {
        String prefix = query.trim().toUpperCase(Locale.ROOT) + "%";
        when(mkb10.findActiveByCodePrefix(prefix, DEFAULT_LIMIT)).thenReturn(List.of(Q06_0));

        assertThat(service.search(query, null).items()).singleElement().isEqualTo(item(Q06_0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"СПИНН", "1a", "амиелия", "q_"})
    void otherQuery_isNameSubstring_lowerCased(String query) {
        when(mkb10.findActiveByNamePattern(
                LikePatterns.substring(query.toLowerCase(Locale.ROOT)), DEFAULT_LIMIT))
                .thenReturn(List.of(Q06_0));

        assertThat(service.search(query, null).items()).containsExactly(item(Q06_0));
    }

    @Test
    void patternCharactersInQuery_areSearchedLiterally() {
        when(mkb10.findActiveByNamePattern("%50!%%", DEFAULT_LIMIT)).thenReturn(List.of());

        assertThat(service.search("50%", null).items()).isEmpty();
    }

    @Test
    void requestedLimit_isPassedToQuery() {
        when(mkb10.findActiveByCodePrefix("Q06%", Limit.of(5))).thenReturn(List.of(Q06_0));

        assertThat(service.search("Q06", 5).items()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void limitOutOfBounds_isValidationError_withoutQuery(int limit) {
        assertThatThrownBy(() -> service.search("Q06", limit))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo("limit"));
        verifyNoInteractions(mkb10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Q", "  Q  ", "\uD83D\uDE00"})
    void queryShorterThanMinimum_isValidationErrorOnQuery_namingRule(String query) {
        assertThatThrownBy(() -> service.search(query, null))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("query");
                    assertThat(invalid.getMessage()).isEqualTo("не короче 2 символов");
                });
        verifyNoInteractions(mkb10);
    }

    @Test
    void twoCharactersOutsideBmp_areSearched() {
        String query = OUTSIDE_BMP.repeat(2);
        when(mkb10.findActiveByNamePattern(LikePatterns.substring(query), DEFAULT_LIMIT)).thenReturn(List.of(Q06_0));

        assertThat(service.search(query, null).items()).containsExactly(item(Q06_0));
    }

    @Test
    void queryAtMaximumLength_isSearched() {
        String query = "ж".repeat(MAX_QUERY_LENGTH);
        when(mkb10.findActiveByNamePattern(LikePatterns.substring(query), DEFAULT_LIMIT)).thenReturn(List.of());

        assertThat(service.search(query, null).items()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q", " "})
    void queryLongerThanMaximum_asReceived_isValidationErrorOnQuery_namingRule(String filler) {
        String query = " " + filler.repeat(MAX_QUERY_LENGTH - 1) + " ";

        assertThatThrownBy(() -> service.search(query, null))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("query");
                    assertThat(invalid.getMessage()).isEqualTo("не длиннее 100 символов");
                });
        verifyNoInteractions(mkb10);
    }

    @Test
    void queryAtMaximumLength_withCharacterOutsideBmp_isSearched() {
        String query = "ж".repeat(MAX_QUERY_LENGTH - 1) + OUTSIDE_BMP;
        assertThat(query).as("единиц UTF-16 в строке").hasSize(MAX_QUERY_LENGTH + 1);
        when(mkb10.findActiveByNamePattern(LikePatterns.substring(query), DEFAULT_LIMIT)).thenReturn(List.of(Q06_0));

        assertThat(service.search(query, null).items()).containsExactly(item(Q06_0));
    }

    @Test
    void queryLongerThanMaximum_withLimitOutOfBounds_isValidationErrorOnQuery() {
        String query = "ж".repeat(MAX_QUERY_LENGTH + 1);

        assertThatThrownBy(() -> service.search(query, 0))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        invalid -> assertThat(invalid.field()).isEqualTo("query"));
        verifyNoInteractions(mkb10);
    }

    @Test
    void queryLength_isLimitedByMkb10Setting() {
        Mkb10QueryService configured = new Mkb10QueryService(new Mkb10Lookup(mkb10),
                TestApiProperties.withMkb10InputLimits(CONFIGURED_MAX_QUERY_LENGTH, CONFIGURED_MAX_CODES));
        String query = "ж".repeat(CONFIGURED_MAX_QUERY_LENGTH);
        when(mkb10.findActiveByNamePattern(LikePatterns.substring(query), DEFAULT_LIMIT)).thenReturn(List.of(Q06_0));

        assertThat(configured.search(query, null).items()).containsExactly(item(Q06_0));
        assertThatThrownBy(() -> configured.search(query + "ж", null))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("query");
                    assertThat(invalid.getMessage()).isEqualTo("не длиннее 5 символов");
                });
    }

    @Test
    void codesCount_isLimitedByMkb10Setting() {
        Mkb10QueryService configured = new Mkb10QueryService(new Mkb10Lookup(mkb10),
                TestApiProperties.withMkb10InputLimits(CONFIGURED_MAX_QUERY_LENGTH, CONFIGURED_MAX_CODES));
        when(mkb10.findByCodes(distinctCodes(CONFIGURED_MAX_CODES))).thenReturn(List.of(Q06_0));

        assertThat(configured.byCodes(distinctCodes(CONFIGURED_MAX_CODES)).items()).containsExactly(item(Q06_0));
        assertThatThrownBy(() -> configured.byCodes(distinctCodes(CONFIGURED_MAX_CODES + 1)))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("codes");
                    assertThat(invalid.getMessage()).isEqualTo("не больше 3 кодов");
                });
    }

    @Test
    void codesAtMaximumCount_withRepeat_areLookedUp() {
        List<String> codes = Stream.concat(distinctCodes(MAX_CODES - 1).stream(), Stream.of("A00")).toList();
        when(mkb10.findByCodes(distinctCodes(MAX_CODES - 1))).thenReturn(List.of(Q06_0));

        assertThat(service.byCodes(codes).items()).containsExactly(item(Q06_0));
    }

    @Test
    void codesOverMaximumCount_asReceived_isValidationErrorOnCodes_namingRule() {
        List<String> codes = Stream.concat(distinctCodes(MAX_CODES).stream(), Stream.of("A00")).toList();

        assertThatThrownBy(() -> service.byCodes(codes))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("codes");
                    assertThat(invalid.getMessage()).isEqualTo("не больше 100 кодов");
                });
        verifyNoInteractions(mkb10);
    }

    @Test
    void byCodes_trimsUpperCasesAndDeduplicates_skippingBlank() {
        when(mkb10.findByCodes(List.of("I25.1", "Z99"))).thenReturn(List.of(Q06_0));

        Mkb10Response response = service.byCodes(List.of(" i25.1 ", "", "Z99", "z99", "  "));

        assertThat(response.items()).containsExactly(item(Q06_0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A00,A01", ",", "A00, "})
    void codeWithComma_isValidationErrorOnCodes_namingRule(String code) {
        assertThatThrownBy(() -> service.byCodes(List.of(code, "A02")))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("codes");
                    assertThat(invalid.getMessage()).isEqualTo("коды через запятую передаются одним параметром codes");
                });
        verifyNoInteractions(mkb10);
    }

    @Test
    void byCodes_withoutAnyCode_isValidationErrorOnCodes() {
        assertThatThrownBy(() -> service.byCodes(List.of(" ", "")))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("codes");
                    assertThat(invalid.getMessage()).isEqualTo("хотя бы один код");
                });
        verifyNoInteractions(mkb10);
    }

    private static List<String> distinctCodes(int count) {
        return IntStream.range(0, count).mapToObj(index -> "A%02d".formatted(index)).toList();
    }

    private static Mkb10Code item(Mkb10Row row) {
        return new Mkb10Code(row.id(), row.externalId(), row.code(), row.name(), row.hierarchyLevel(),
                row.parentCode(), row.isArchive());
    }
}
