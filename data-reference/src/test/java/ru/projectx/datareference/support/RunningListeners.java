package ru.projectx.datareference.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;

public final class RunningListeners {

    private static final Duration LEADERSHIP_TIMEOUT = Duration.ofSeconds(15);

    public static void assertAllRunning(RabbitListenerEndpointRegistry listeners) {
        assertThat(listeners.getListenerContainerIds())
                .as("контейнеры слушателей очередей 1С")
                .isNotEmpty();
        await().atMost(LEADERSHIP_TIMEOUT).untilAsserted(() -> assertThat(listeners.getListenerContainerIds())
                .allSatisfy(id -> assertThat(listeners.getListenerContainer(id).isRunning())
                        .as("слушатель %s не запущен: экземпляр не ведущий, auto-startup выключен или контейнер остановлен",
                                id)
                        .isTrue()));
    }

    private RunningListeners() {
    }
}
