package ru.projectx.contracts.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Межсервисный брокер контура: по нему сервис Заявок присылает задачи на выпуск и их отмены, а сервис Договоров
 * отвечает состоянием выпуска. Имена очередей — адреса контура, умолчаний в коде у них нет.
 */
@Validated
@ConfigurationProperties("projectx.brokers.internal")
public record InternalBrokerProperties(
        @Valid @NotNull BrokerConnectionProperties connection,
        boolean listenerAutoStartup,
        @Valid @NotNull Queues queues) implements BrokerProperties {

    /** Очереди межсервисного брокера: приём задач и отмен, публикация состояния выпуска. Имена должны быть разными. */
    public record Queues(@NotBlank String taskChanges, @NotBlank String statusChanges) {

        public Queues {
            QueueNames.requireDistinct(taskChanges, statusChanges);
        }
    }
}
