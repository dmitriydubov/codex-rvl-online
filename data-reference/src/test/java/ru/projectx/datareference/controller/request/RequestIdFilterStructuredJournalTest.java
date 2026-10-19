package ru.projectx.datareference.controller.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.projectx.datareference.support.StructuredJournal;

@ExtendWith(OutputCaptureExtension.class)
class RequestIdFilterStructuredJournalTest {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilterStructuredJournalTest.class);

    private static final String REQUEST_ID = "hr-7f3a.42_b";

    private StructuredJournal journal;

    @BeforeEach
    void startJournal() {
        journal = StructuredJournal.start();
    }

    @AfterEach
    void stopJournal() {
        journal.close();
    }

    @Test
    void recordDuringRequest_carriesRequestId(CapturedOutput output) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, REQUEST_ID);
        FilterChain handler = (handled, response) -> log.info("запрос обработан");

        new RequestIdFilter().doFilter(request, new MockHttpServletResponse(), handler);

        assertThat(StructuredJournal.recordWith(output, "запрос обработан").path(RequestIdFilter.MDC_KEY).asString())
                .isEqualTo(REQUEST_ID);
    }

    @Test
    void recordAfterRequest_carriesNoRequestId(CapturedOutput output) throws ServletException, IOException {
        new RequestIdFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
        });

        log.info("после запроса");

        assertThat(StructuredJournal.recordWith(output, "после запроса").has(RequestIdFilter.MDC_KEY)).isFalse();
    }
}
