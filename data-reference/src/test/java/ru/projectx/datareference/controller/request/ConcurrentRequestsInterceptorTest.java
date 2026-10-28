package ru.projectx.datareference.controller.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.projectx.datareference.exception.RestOverloadException;
import ru.projectx.datareference.service.rest.RestReadAdmission;
import ru.projectx.datareference.support.TestApiProperties;

class ConcurrentRequestsInterceptorTest {

    private static final int LIMIT = 1;
    private static final Object HANDLER = new Object();

    private final RestReadAdmission admission =
            new RestReadAdmission(TestApiProperties.withMaxConcurrentRequests(LIMIT), new SimpleMeterRegistry());
    private final ConcurrentRequestsInterceptor interceptor = new ConcurrentRequestsInterceptor(admission);

    @Test
    void admittedRequest_proceeds_andCountsAsInFlight() {
        boolean proceed = interceptor.preHandle(request(), new MockHttpServletResponse(), HANDLER);

        assertThat(proceed).isTrue();
        assertThat(admission.inFlight()).isEqualTo(1);
    }

    @Test
    void requestBeyondLimit_isRejectedWithTheLimit_withoutTakingAPlace() {
        interceptor.preHandle(request(), new MockHttpServletResponse(), HANDLER);

        assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(), HANDLER))
                .isInstanceOf(RestOverloadException.class)
                .satisfies(overload -> assertThat(((RestOverloadException) overload).limit()).isEqualTo(LIMIT))
                .hasMessage("превышен предел одновременно обрабатываемых запросов: 1");
        assertThat(admission.inFlight()).isEqualTo(1);
    }

    @Test
    void completedRequest_freesItsPlace_evenAfterFailure() {
        interceptor.preHandle(request(), new MockHttpServletResponse(), HANDLER);

        interceptor.afterCompletion(request(), new MockHttpServletResponse(), HANDLER, new IllegalStateException());

        assertThat(admission.inFlight()).isZero();
        assertThat(interceptor.preHandle(request(), new MockHttpServletResponse(), HANDLER)).isTrue();
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/internal/v1/programs/1");
    }
}
