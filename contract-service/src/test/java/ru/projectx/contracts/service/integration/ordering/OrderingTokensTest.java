package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import tools.jackson.databind.json.JsonMapper;

class OrderingTokensTest {

    private static final UUID CORRELATION = UUID.fromString("0196f0a2-0000-7000-8000-0000000000ac");
    private static final Duration WELL_BEFORE_THE_SERVER_GIVES_UP = Duration.ofSeconds(3);

    @Test
    void managerWithoutAnAuthorizedClient_isATemporaryTokenFailure() {
        OrderingTokens tokens = new OrderingTokens(mock(OAuth2AuthorizedClientManager.class),
                mock(OAuth2AuthorizedClientService.class));

        assertThatThrownBy(tokens::accessToken)
                .isInstanceOfSatisfying(OrderingUnavailableException.class,
                        unavailable -> assertThat(unavailable.operation()).isEqualTo(OrderingOperation.TOKEN))
                .hasMessageContaining("токен не выдан");
    }

    @Test
    void silentTokenServer_isCutOffByTheTokenReadTimeout() throws IOException {
        try (OrderingTestServer server = new OrderingTestServer()) {
            server.token(server.silence());
            OrderingHttpClient client = OrderingHttpClients.create(
                    OrderingSettings.at(server.orderingUrl(), server.tokenUri()), JsonMapper.builder().build(),
                    new OrderingMetrics(new SimpleMeterRegistry()),
                    new MovableClock(Instant.parse("2026-02-10T07:05:00Z")));

            assertTimeoutPreemptively(WELL_BEFORE_THE_SERVER_GIVES_UP,
                    () -> assertThatThrownBy(() -> client.session(CORRELATION)).isInstanceOfSatisfying(OrderingUnavailableException.class,
                            unavailable -> assertThat(unavailable.operation()).isEqualTo(OrderingOperation.TOKEN)));
        }
    }
}
