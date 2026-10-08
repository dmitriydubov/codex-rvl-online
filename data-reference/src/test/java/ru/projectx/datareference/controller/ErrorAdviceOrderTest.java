package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;

/**
 * Spring берёт первый по порядку обработчик ошибок с подходящим методом, даже если у следующего метод точнее.
 * Без явного {@code @Order} обработчик любого исключения мог бы ответить 500 на 404 или 405, поэтому здесь
 * обработчики зарегистрированы в обратном порядке.
 */
class ErrorAdviceOrderTest {

    private final FailingController controller = new FailingController();
    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.withRequestId(controller,
                StandaloneMvc.serverFailureAdvice(), new RequestErrorAdvice(), new ReferenceErrorAdvice());
    }

    @Test
    void referenceNotFound_reachesReferenceAdvice_notServerFailure() {
        controller.failWith(ReferenceNotFoundException.lpu(UUID.fromString("0a9e0000-0000-4000-8000-000000000005")));

        MvcTestResult result = mvc.get().uri(FailingController.FAILING).exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("LPU_NOT_FOUND");
    }

    @Test
    void invalidRequest_reachesRequestAdvice_notServerFailure() {
        controller.failWith(new InvalidRequestException("query", "не короче трёх символов"));

        MvcTestResult result = mvc.get().uri(FailingController.FAILING).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void otherMethod_reachesRequestAdvice_notServerFailure() {
        MvcTestResult result = mvc.method(HttpMethod.POST).uri(FailingController.READ_ONLY).exchange();

        assertThat(result).hasStatus(405);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unexpectedFailure_stillReachesServerFailure() {
        controller.failWith(new IllegalStateException("сбой"));

        MvcTestResult result = mvc.get().uri(FailingController.FAILING).exchange();

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("INTERNAL_ERROR");
    }
}
