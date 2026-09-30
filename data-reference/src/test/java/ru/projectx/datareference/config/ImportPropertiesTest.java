package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Задержка первого тика планировщиков: как она привязывается и какое значение не пускает
 * сервис в работу.
 *
 * <p>Привязка настоящая, Boot-овская, но без приложения, базы и брокера: контекст из одной
 * записи. Этого достаточно, потому что проверка идёт на старте — неверное значение обязано
 * уронить запуск, а не всплыть потом тиком не в тот момент.
 */
class ImportPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ImportPropertiesBinding.class);

    @Test
    void initialDelay_defaultsToThirtySeconds() {
        context.run(started -> assertThat(started.getBean(ImportProperties.class).initialDelay())
                .isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void initialDelay_isBoundFromProperty() {
        context.withPropertyValues("projectx.import.initial-delay=2m")
                .run(started -> assertThat(started.getBean(ImportProperties.class).initialDelay())
                        .isEqualTo(Duration.ofMinutes(2)));
    }

    /** Ноль — осознанное «без задержки», как было до неё: это значение, а не ошибка. */
    @Test
    void zeroInitialDelay_isAccepted() {
        context.withPropertyValues("projectx.import.initial-delay=0s")
                .run(started -> assertThat(started.getBean(ImportProperties.class).initialDelay())
                        .isZero());
    }

    /**
     * Отрицательную задержку {@code @Scheduled} молча принимает за нулевую, и первый тик
     * случается сразу на старте — ровно то, от чего задержка защищает. Поэтому такое значение
     * не должно пускать сервис в работу вовсе.
     */
    @Test
    void negativeInitialDelay_failsStartup() {
        context.withPropertyValues("projectx.import.initial-delay=-1s")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("initialDelay"));
    }

    @Test
    void unreadableInitialDelay_failsStartup() {
        context.withPropertyValues("projectx.import.initial-delay=soon")
                .run(started -> assertThat(started).getFailure()
                        .cause().hasMessageContaining("projectx.import.initial-delay"));
    }

    @EnableConfigurationProperties(ImportProperties.class)
    static class ImportPropertiesBinding {
    }
}
