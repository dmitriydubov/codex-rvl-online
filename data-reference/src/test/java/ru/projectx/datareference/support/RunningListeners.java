package ru.projectx.datareference.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;

/**
 * Предпроверка тестов приёма: контейнеры слушателей очередей 1С есть и запущены. Без неё при выключенных слушателях
 * тест падал бы по таймауту, не называя причину.
 */
public final class RunningListeners {

    public static void assertAllRunning(RabbitListenerEndpointRegistry listeners) {
        assertThat(listeners.getListenerContainerIds())
                .as("контейнеры слушателей очередей 1С")
                .isNotEmpty()
                .allSatisfy(id -> assertThat(listeners.getListenerContainer(id).isRunning())
                        .as("слушатель %s не запущен: auto-startup выключен или контейнер остановлен", id)
                        .isTrue());
    }

    private RunningListeners() {
    }
}
