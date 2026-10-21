package ru.projectx.datareference.controller.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;

class AdviceHandlersTest {

    @Test
    void failureReachesTheClosestHandlerOfTheFirstAdviceInOrder() {
        List<Exception> failures = List.of(new MissingServletRequestParameterException("id", "UUID"));

        assertThat(AdviceHandlers.unreachedBy(failures))
                .contains("RequestErrorAdvice.unsatisfiedParameters", "ReferenceErrorAdvice.referenceNotFound")
                .doesNotContain("RequestErrorAdvice.missingParameter");
    }

    @Test
    void handlerOfASupertypeStaysUnreachedWhenTheFailureHasItsOwnHandler() {
        List<Exception> failures = List.of(new UnsatisfiedServletRequestParameterException(new String[] {"id"}, Map.of()));

        assertThat(AdviceHandlers.unreachedBy(failures))
                .contains("ServerFailureAdvice.unexpected")
                .doesNotContain("RequestErrorAdvice.unsatisfiedParameters");
    }

    @Test
    void failureWithoutItsOwnHandlerReachesTheCatchAllHandler() {
        assertThat(AdviceHandlers.unreachedBy(List.of(new IllegalStateException("сбой"))))
                .doesNotContain("ServerFailureAdvice.unexpected")
                .contains("RequestErrorAdvice.invalidRequest");
    }
}
