package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.config.ReferenceProperties;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;
import tools.jackson.databind.json.JsonMapper;

class ReferenceRestClientsTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);
    private static final UUID POLICY_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");

    @Test
    void silentServer_isCutOffByTheReadTimeout() throws IOException, InterruptedException {
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            AtomicBoolean closedByClient = new AtomicBoolean();
            Thread acceptor = Thread.ofVirtual().start(() -> acceptAndStaySilent(silent, closedByClient));
            ReferenceHttpClient client = client(silent.getLocalPort(), 0);

            assertThatThrownBy(() -> client.policy(POLICY_ID))
                    .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                            unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.TIMEOUT))
                    .hasRootCauseInstanceOf(HttpTimeoutException.class);
            acceptor.join(READ_TIMEOUT.multipliedBy(10));
            assertThat(closedByClient).isTrue();
        }
    }

    @Test
    void closedPort_isAConnectionFailureRetriedTheConfiguredNumberOfTimes() throws IOException {
        int closedPort;
        try (ServerSocket reserved = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = reserved.getLocalPort();
        }
        ReferenceHttpClient client = client(closedPort, 1);

        assertThatThrownBy(() -> client.policy(POLICY_ID))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.CONNECTION_FAILED))
                .hasRootCauseInstanceOf(IOException.class);
    }

    private static void acceptAndStaySilent(ServerSocket silent, AtomicBoolean closedByClient) {
        try (Socket connection = silent.accept()) {
            connection.getInputStream().transferTo(OutputStream.nullOutputStream());
            closedByClient.set(true);
        }
        catch (IOException abruptlyClosed) {
            closedByClient.set(true);
        }
    }

    private static ReferenceHttpClient client(int port, int retries) {
        ReferenceProperties settings = new ReferenceProperties("http://127.0.0.1:" + port + "/internal/v1",
                Duration.ofSeconds(1), READ_TIMEOUT, new ReferenceProperties.Retry(retries, Duration.ZERO));
        return new ReferenceHttpClient(ReferenceRestClients.builder(settings, JsonMapper.shared()).build(),
                ReferenceRetries.of(settings.retry()));
    }
}
