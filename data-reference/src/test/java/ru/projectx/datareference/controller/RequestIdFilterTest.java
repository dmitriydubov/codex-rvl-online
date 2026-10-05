package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Присланный X-Request-Id: допустимый возвращается как есть, иначе — новый UUID, запрос не отклоняется. */
class RequestIdFilterTest {

    private static final String SIXTY_FOUR =
            "0123456789abcdef" + "0123456789abcdef" + "0123456789abcdef" + "0123456789abcdef";

    private final RequestIdFilter filter = new RequestIdFilter();

    @ParameterizedTest
    @ValueSource(strings = {"a", "Z", "9", "a.b_c-d", "3F2B7C1D-8E4A-4B6C-9D0E-1A2B3C4D5E6F", SIXTY_FOUR})
    void acceptableRequestId_isReturnedAsIs(String sent) throws ServletException, IOException {
        MockHttpServletResponse response = filter(sent);

        assertThat(response.getHeaders(RequestIdFilter.HEADER)).containsExactly(sent);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a b", "a:b", "a/b", "a;b", "привет", "a\tb", SIXTY_FOUR + "0"})
    void unacceptableRequestId_isReplacedWithNewUuid(String sent) throws ServletException, IOException {
        MockHttpServletResponse response = filter(sent);

        assertThat(response.getHeaders(RequestIdFilter.HEADER)).singleElement()
                .satisfies(issued -> assertThat(UUID.fromString(issued).toString()).isEqualTo(issued))
                .isNotEqualTo(sent);
    }

    @Test
    void missingRequestId_isReplacedWithNewUuid() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeaders(RequestIdFilter.HEADER)).singleElement()
                .satisfies(issued -> assertThat(UUID.fromString(issued).toString()).isEqualTo(issued));
    }

    @Test
    void issuedRequestIds_differBetweenRequests() throws ServletException, IOException {
        assertThat(filter("a b").getHeader(RequestIdFilter.HEADER))
                .isNotEqualTo(filter("a b").getHeader(RequestIdFilter.HEADER));
    }

    /** В MDC на время обработки — то же значение, что в заголовке ответа; после — ничего. */
    @Test
    void requestIdIsInMdcDuringChain_andRemovedAfter() throws ServletException, IOException {
        AtomicReference<String> seenInChain = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "a:b");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> seenInChain.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(seenInChain.get()).isEqualTo(response.getHeader(RequestIdFilter.HEADER));
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    private MockHttpServletResponse filter(String sent) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, sent);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
