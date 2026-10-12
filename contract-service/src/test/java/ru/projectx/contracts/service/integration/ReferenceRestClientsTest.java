package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.config.ReferenceProperties;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;
import tools.jackson.databind.json.JsonMapper;

/**
 * Настоящие таймауты клиента на локальном сокете: сервер, который принял соединение и молчит, обрывается по таймауту
 * чтения, а закрытый порт — отказ соединения. Без таймаута чтения клиент JDK ждал бы ответ без предела.
 */
class ReferenceRestClientsTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);
    private static final UUID POLICY_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");

    @Test
    void silentServer_isCutOffByTheReadTimeout() throws IOException, InterruptedException {
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            AtomicInteger accepted = new AtomicInteger();
            Thread acceptor = Thread.ofVirtual().start(() -> acceptAndStaySilent(silent, accepted));
            ReferenceHttpClient client = client(silent.getLocalPort(), 0);

            assertThatThrownBy(() -> client.policy(POLICY_ID))
                    .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                            unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.TIMEOUT))
                    .hasRootCauseInstanceOf(HttpTimeoutException.class);
            acceptor.join(READ_TIMEOUT.multipliedBy(10));
            assertThat(accepted).hasValue(1);
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

    private static void acceptAndStaySilent(ServerSocket silent, AtomicInteger accepted) {
        try (Socket connection = silent.accept()) {
            accepted.incrementAndGet();
            // Читаем запрос и молчим, пока клиент сам не закроет соединение по таймауту.
            while (connection.getInputStream().read() >= 0) {
                continue;
            }
        }
        catch (IOException closed) {
            // Клиент оборвал соединение: именно этого тест и ждёт.
            accepted.compareAndSet(0, 0);
        }
    }

    private static ReferenceHttpClient client(int port, int retries) {
        ReferenceProperties settings = new ReferenceProperties("http://127.0.0.1:" + port + "/internal/v1",
                Duration.ofSeconds(1), READ_TIMEOUT, new ReferenceProperties.Retry(retries, Duration.ZERO));
        return new ReferenceHttpClient(ReferenceRestClients.builder(settings, JsonMapper.shared()).build(),
                ReferenceRetries.of(settings.retry()));
    }
}
