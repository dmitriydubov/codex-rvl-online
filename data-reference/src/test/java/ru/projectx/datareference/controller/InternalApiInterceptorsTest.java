package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import ru.projectx.datareference.controller.request.ConcurrentRequestsInterceptor;
import ru.projectx.datareference.service.rest.RestReadAdmission;
import ru.projectx.datareference.support.TestApiProperties;

class InternalApiInterceptorsTest {

    private final ConcurrentRequestsInterceptor concurrentRequests = new ConcurrentRequestsInterceptor(
            new RestReadAdmission(TestApiProperties.defaults(), new SimpleMeterRegistry()));

    @Test
    void concurrentRequestsInterceptor_isMappedToEveryInternalApiPathOnly() {
        ExposedRegistry registry = new ExposedRegistry();

        new InternalApiInterceptors(concurrentRequests).addInterceptors(registry);

        assertThat(registry.registered()).singleElement()
                .isInstanceOfSatisfying(MappedInterceptor.class, mapped -> {
                    assertThat(mapped.getInterceptor()).isSameAs(concurrentRequests);
                    assertThat(mapped.getIncludePathPatterns()).containsExactly("/internal/v1/**");
                    assertThat(mapped.matches(request("/internal/v1/policies/1"))).isTrue();
                    assertThat(mapped.matches(request("/internal/v1/lpu/1/services"))).isTrue();
                    assertThat(mapped.matches(request("/livez"))).isFalse();
                    assertThat(mapped.matches(request("/readyz"))).isFalse();
                    assertThat(mapped.matches(request("/actuator/health"))).isFalse();
                    assertThat(mapped.matches(request("/internal/v2/policies/1"))).isFalse();
                });
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    private static final class ExposedRegistry extends InterceptorRegistry {

        List<Object> registered() {
            return getInterceptors();
        }
    }
}
