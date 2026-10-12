package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.projectx.contracts.config.ReferenceProperties;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.support.StandExample;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Сетевая сторона клиента: сквозной {@code X-Request-Id} в каждом запросе и перевод сетевых отказов в недоступность
 * справочника с причиной — таймаут отдельно от обрыва соединения, чтобы в журнале было видно, что именно случилось.
 */
class ReferenceTransportTest {

    private static final String BASE = "http://reference.example/internal/v1";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final UUID POLICY_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final int RETRIES = 1;

    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private final ReferenceProperties settings = new ReferenceProperties(BASE, Duration.ofSeconds(2),
            Duration.ofSeconds(7), new ReferenceProperties.Retry(RETRIES, Duration.ZERO));
    private final RestClient.Builder builder = ReferenceRestClients.builder(settings, JSON);
    private final MockRestServiceServer reference = MockRestServiceServer.bindTo(builder).build();
    private final ReferenceHttpClient client =
            new ReferenceHttpClient(builder.build(), ReferenceRetries.of(settings.retry()));

    @AfterEach
    void clearMdc() {
        MDC.remove(RequestIds.MDC_KEY);
    }

    @Test
    void requestId_fromCurrentProcessing_isPassedThrough() {
        MDC.put(RequestIds.MDC_KEY, "task-2026-000123");
        reference.expect(once(), requestTo(BASE + "/policies/" + POLICY_ID + "/contract-view"))
                .andExpect(header(RequestIds.HEADER, "task-2026-000123"))
                .andRespond(policyExample());

        client.policy(POLICY_ID);

        reference.verify();
    }

    @Test
    void requestId_withoutCurrentProcessing_isAFreshUuid() {
        reference.expect(once(), requestTo(BASE + "/policies/" + POLICY_ID + "/contract-view"))
                .andExpect(header(RequestIds.HEADER, Matchers.matchesPattern(UUID_PATTERN)))
                .andRespond(policyExample());

        client.policy(POLICY_ID);

        reference.verify();
    }

    @ParameterizedTest
    @MethodSource("transportFailures")
    void transportFailure_isUnavailableWithItsReasonAfterRetries(IOException failure, Reason reason) {
        reference.expect(times(RETRIES + 1), requestTo(BASE + "/policies/" + POLICY_ID + "/contract-view"))
                .andRespond(withException(failure));

        assertThatThrownBy(() -> client.policy(POLICY_ID))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(reason))
                .hasRootCause(failure);
        reference.verify();
    }

    static Object[][] transportFailures() {
        return new Object[][] {
            {new HttpTimeoutException("нет ответа"), Reason.TIMEOUT},
            {new HttpConnectTimeoutException("нет соединения"), Reason.TIMEOUT},
            {new ConnectException("отказано"), Reason.CONNECTION_FAILED},
            {new IOException("оборвано"), Reason.CONNECTION_FAILED},
        };
    }

    private static org.springframework.test.web.client.ResponseCreator policyExample() {
        return withSuccess(StandExample.load("10-policy-contract-view.json")
                .body(Map.of("policy_id", POLICY_ID.toString())), MediaType.APPLICATION_JSON);
    }
}
