package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.service.Mkb10QueryService;

/** R-07: подсказка по МКБ-10, раскрытие перечня кодов и запрос без ключа. */
@ExtendWith(MockitoExtension.class)
class Mkb10ControllerTest {

    private static final String MKB10 = InternalApi.BASE_PATH + "/mkb10";
    private static final UUID CODE_ID = UUID.fromString("3b100000-0000-4000-8000-000000000001");
    private static final UUID CODE_EXTERNAL_ID = UUID.fromString("3b100000-0000-4000-8000-000000000002");

    @Mock
    private Mkb10QueryService mkb10;

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

    /** {@code produces} отсекает запрос ещё при выборе обработчика; тело 406 — забота Ш25. */
    @Test
    void search_acceptingOnlyXml_isNotAcceptable_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(MKB10 + "?query={query}", "Q06").accept(MediaType.APPLICATION_XML)
                .exchange();

        assertThat(result).hasStatus(406);
        verifyNoInteractions(mkb10);
    }

    private static Mkb10Response codes(String code) {
        return new Mkb10Response(List.of(
                new Mkb10Response.Item(CODE_ID, CODE_EXTERNAL_ID, code, "Наименование кода", 2, null, false)));
    }
}
