package ru.projectx.datareference.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;

/**
 * Предпроверка тестов приёма из брокера: контейнеры слушателей очередей 1С есть и запущены.
 *
 * <p>Без неё тест, ждущий записи сообщения, при выключенных слушателях падает по таймауту
 * с «условие не выполнено», не называя причину: сообщение лежит в очереди, и забирать
 * его некому.
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
