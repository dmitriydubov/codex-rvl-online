package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.dto.Mkb10Code;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.service.Mkb10Reads;

/**
 * Ручка МКБ-10 выбирает режим по ключу запроса: подсказка по строке или раскрытие перечня кодов. Запрос без
 * ключа и запрос с ключами обоих режимов сразу отклоняются ошибкой вызывающего, и сервис чтения не вызывается.
 */
@ExtendWith(MockitoExtension.class)
class Mkb10ControllerTest {

    private static final String MKB10 = InternalApi.BASE_PATH + "/mkb10";
    private static final UUID CODE_ID = UUID.fromString("3b100000-0000-4000-8000-000000000001");
    private static final UUID CODE_EXTERNAL_ID = UUID.fromString("3b100000-0000-4000-8000-000000000002");
    private static final String QUERY_WITH_CODES_REASON = "query и codes вместе не передаются: нужен один из них";

    @Mock
    private Mkb10Reads mkb10;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.of(new Mkb10Controller(mkb10));
    }

    @Test
    void search_passesQueryAndLimit_andReturnsFoundCodes_asJson() {
        when(mkb10.search("Q06", 5)).thenReturn(codes("Q06"));

        MvcTestResult result = mvc.get().uri(MKB10 + "?query={query}&limit={limit}", "Q06", 5).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.items[*].code").asArray().containsExactly("Q06");
    }

    @Test
    void search_withoutLimit_leavesLimitToService() {
        when(mkb10.search("Q06", null)).thenReturn(codes("Q06"));

        MvcTestResult result = mvc.get().uri(MKB10 + "?query={query}", "Q06").exchange();

        assertThat(result).bodyJson().extractingPath("$.items[*].code").asArray().containsExactly("Q06");
    }

    @Test
    void byCodes_splitsCommaSeparatedList() {
        when(mkb10.byCodes(List.of("I25.1", "Z99"))).thenReturn(codes("I25.1"));

        MvcTestResult result = mvc.get().uri(MKB10 + "?codes={codes}", "I25.1,Z99").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].code").asArray().containsExactly("I25.1");
    }

    @Test
    void withoutKey_isValidationErrorNamingQuery() {
        MvcTestResult result = mvc.get().uri(MKB10).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("query");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo("нужен query либо codes");
        verifyNoInteractions(mkb10);
    }

    /**
     * Строка поиска и перечень кодов — два разных режима. Запрос с обоими ключами отклоняется целиком, а не
     * уходит внутренней ошибкой из-за двух равных по точности ручек. Пустой {@code codes} тоже считается
     * переданным ключом, а недопустимый {@code limit} не проверяется: режим не выбран.
     */
    @ParameterizedTest
    @ValueSource(strings = {"?query=I25&codes=I25.1,Q06", "?query=I25&codes=", "?query=I25&codes=I25.1&limit=0"})
    void queryWithCodes_isValidationErrorNamingQuery_withoutAskingService(String parameters) {
        MvcTestResult result = mvc.get().uri(MKB10 + parameters).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("query");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(QUERY_WITH_CODES_REASON);
        verifyNoInteractions(mkb10);
    }

    /** Условие «ключа другого режима нет» смотрит только на query и codes: посторонний параметр ему не мешает. */
    @Test
    void searchWithUnrelatedParameter_isStillSearch() {
        when(mkb10.search("Q06", null)).thenReturn(codes("Q06"));

        MvcTestResult result = mvc.get().uri(MKB10 + "?query={query}&foo=1", "Q06").exchange();

        assertThat(result).bodyJson().extractingPath("$.items[*].code").asArray().containsExactly("Q06");
    }

    /** {@code limit} у перечня кодов не используется и не делает запрос подсказкой. */
    @Test
    void byCodesWithLimit_isStillLookupByCodes() {
        when(mkb10.byCodes(List.of("I25.1"))).thenReturn(codes("I25.1"));

        MvcTestResult result = mvc.get().uri(MKB10 + "?codes={codes}&limit=5", "I25.1").exchange();

        assertThat(result).bodyJson().extractingPath("$.items[*].code").asArray().containsExactly("I25.1");
    }

    /** Ручка отдаёт только JSON, поэтому запрос, который принимает только XML, отсекается ещё при выборе ручки. */
    @Test
    void search_acceptingOnlyXml_isNotAcceptable_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(MKB10 + "?query={query}", "Q06").accept(MediaType.APPLICATION_XML)
                .exchange();

        assertThat(result).hasStatus(406);
        verifyNoInteractions(mkb10);
    }

    private static Mkb10Response codes(String code) {
        return new Mkb10Response(List.of(
                new Mkb10Code(CODE_ID, CODE_EXTERNAL_ID, code, "Наименование кода", 2, null, false)));
    }
}
