package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.projectx.contracts.service.RequestIds;

class RequestIdFilterTest {

    private static final String UPLOAD = "/internal/v1/requests/61a7982c-a0b4-4bbf-9527-60b40b752803/participants/"
            + "6778e8c2-985a-4d54-9bd3-0f6403b104d6/attachments";

    private final RequestIdFilter filter = new RequestIdFilter();
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void collectJournal() {
        journal.start();
        logger().addAppender(journal);
    }

    @AfterEach
    void stopCollecting() {
        logger().detachAppender(journal);
        MDC.clear();
    }

    @Test
    void acceptableHeader_isInMdcDuringTheCallAndInTheResponse() throws ServletException, IOException {
        AtomicReference<String> duringCall = new AtomicReference<>();

        filter.doFilter(request(UPLOAD, "Req-01._x"), response, chainSeeing(duringCall));

        assertThat(duringCall).hasValue("Req-01._x");
        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo("Req-01._x");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "req/01"})
    void unacceptableHeader_isReplacedByAUuidInMdcAndResponse(String header) throws ServletException, IOException {
        AtomicReference<String> duringCall = new AtomicReference<>();

        filter.doFilter(request(UPLOAD, header), response, chainSeeing(duringCall));

        String issued = response.getHeader(RequestIds.HEADER);
        assertThat(UUID.fromString(issued)).hasToString(issued);
        assertThat(duringCall).hasValue(issued);
    }

    @Test
    void requestId_isRemovedFromMdcAfterTheCallEvenWhenItFails() {
        MockFilterChain failing = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                throw new IllegalStateException("отказ обработчика");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request(UPLOAD, "a"), response, failing))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void call_isJournaledWithMethodPathStatusAndRequestId() throws ServletException, IOException {
        MockFilterChain answering = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                ((MockHttpServletResponse) response).setStatus(409);
            }
        };

        filter.doFilter(request(UPLOAD, "a41f8c02"), response, answering);

        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("синхронный вызов POST " + UPLOAD + " завершён с кодом 409");
            assertThat(event.getMDCPropertyMap()).containsEntry(RequestIds.MDC_KEY, "a41f8c02");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/livez", "/readyz", "/internal/v10/x", "/internal/v1"})
    void callsOutsideTheInternalApi_areNotTouched(String path) throws ServletException, IOException {
        filter.doFilter(request(path, "a"), response, new MockFilterChain());

        assertThat(response.getHeader(RequestIds.HEADER)).isNull();
        assertThat(journal.list).isEmpty();
    }

    private static MockHttpServletRequest request(String path, String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(RequestIds.HEADER, requestId);
        return request;
    }

    private static MockFilterChain chainSeeing(AtomicReference<String> duringCall) {
        return new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                duringCall.set(MDC.get(RequestIds.MDC_KEY));
            }
        };
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
    }
}
