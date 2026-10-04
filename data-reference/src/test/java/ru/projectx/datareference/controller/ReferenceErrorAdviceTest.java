package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.IncompleteReferenceException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;

/**
 * Отказы справочника доходят до потребителя тем же JSON, что и до разделения обработчика ошибок, байт
 * в байт: сервисы Заявок и Договоров разбирают код, текст и перечень записей. Эталоны сняты со старого
 * обработчика на той же сборке без контекста, поэтому имена полей здесь в camelCase.
 *
 * <p>409 и 422 означают ошибку данных в 1С, а не вызывающего. Их пишут в журнал на уровне WARN с кодом,
 * перечнем записей и идентификатором запроса: по этим записям собирают очередь на исправление.
 */
@ExtendWith(OutputCaptureExtension.class)
class ReferenceErrorAdviceTest {

    private static final String REQUEST_ID = "handler-test-1";
    private static final UUID FIRST = UUID.fromString("0a9e0000-0000-4000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("0a9e0000-0000-4000-8000-000000000002");

    private final FailingController controller = new FailingController();
    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.withRequestId(controller, new ReferenceErrorAdvice());
    }

    @Test
    void referenceNotFound_isNotFound_withSameBodyAsBefore() throws UnsupportedEncodingException {
        controller.failWith(ReferenceNotFoundException.program(FIRST));

        MvcTestResult result = request();

        assertThat(result).hasStatus(404);
        assertThat(body(result)).isEqualTo("{\"error\":{\"code\":\"PROGRAM_NOT_FOUND\","
                + "\"message\":\"программа страхования " + FIRST + " не найдена\","
                + "\"details\":[],\"requestId\":\"" + REQUEST_ID + "\"}}");
    }

    @Test
    void ambiguousPolicy_isConflict_withSameBodyAsBefore() throws UnsupportedEncodingException {
        controller.failWith(AmbiguousReferenceException.policy(List.of(FIRST, SECOND)));

        MvcTestResult result = request();

        assertThat(result).hasStatus(409);
        assertThat(body(result)).isEqualTo("{\"error\":{\"code\":\"AMBIGUOUS_POLICY\","
                + "\"message\":\"по номеру полиса в договоре найдено больше одной записи\","
                + "\"details\":[{\"policyId\":\"" + FIRST + "\"},{\"policyId\":\"" + SECOND + "\"}],"
                + "\"requestId\":\"" + REQUEST_ID + "\"}}");
    }

    @Test
    void ambiguousPriceContract_isConflict_withSameBodyAsBefore() throws UnsupportedEncodingException {
        controller.failWith(AmbiguousReferenceException.priceContract(FIRST, List.of(SECOND)));

        MvcTestResult result = request();

        assertThat(result).hasStatus(409);
        assertThat(body(result)).isEqualTo("{\"error\":{\"code\":\"AMBIGUOUS_PRICE_CONTRACT\","
                + "\"message\":\"у ЛПУ " + FIRST + " больше одного действующего кандидата на прайсовый договор\","
                + "\"details\":[{\"lpuContractId\":\"" + SECOND + "\"}],"
                + "\"requestId\":\"" + REQUEST_ID + "\"}}");
    }

    @Test
    void incompleteReference_isUnprocessable_withSameBodyAsBefore() throws UnsupportedEncodingException {
        controller.failWith(IncompleteReferenceException.noActivePriceContract(FIRST));

        MvcTestResult result = request();

        assertThat(result).hasStatus(422);
        assertThat(body(result)).isEqualTo("{\"error\":{\"code\":\"NO_ACTIVE_PRICE_CONTRACT\","
                + "\"message\":\"у ЛПУ " + FIRST + " нет действующего договора, по которому брать цены\","
                + "\"details\":[],\"requestId\":\"" + REQUEST_ID + "\"}}");
    }

    @Test
    void ambiguousReference_isLoggedAsWarning_withCandidatesAndRequestId(CapturedOutput output) {
        controller.failWith(AmbiguousReferenceException.policy(List.of(FIRST)));

        request();

        assertThat(output).contains("WARN", "неоднозначные данные 1С: AMBIGUOUS_POLICY",
                "PolicyCandidate[policyId=" + FIRST + "]", "request_id=" + REQUEST_ID).doesNotContain("ERROR");
    }

    @Test
    void incompleteReference_isLoggedAsWarning_withCodeLpuAndRequestId(CapturedOutput output) {
        controller.failWith(IncompleteReferenceException.noActivePriceContract(FIRST));

        request();

        assertThat(output).contains("WARN", "данных 1С не хватает для ответа: NO_ACTIVE_PRICE_CONTRACT",
                "lpu_id=" + FIRST, "request_id=" + REQUEST_ID).doesNotContain("ERROR");
    }

    private MvcTestResult request() {
        return mvc.get().uri(FailingController.FAILING).header(RequestIdFilter.HEADER, REQUEST_ID).exchange();
    }

    private static String body(MvcTestResult result) throws UnsupportedEncodingException {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
