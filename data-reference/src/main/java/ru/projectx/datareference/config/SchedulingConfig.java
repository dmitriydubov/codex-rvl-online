package ru.projectx.datareference.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Включает работу по расписанию с отдельным пулом потоков. Тесты выключают её свойством
 * {@code projectx.scheduling.enabled=false} и вызывают проходы сами, чтобы проход не менял статусы посреди теста.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "projectx.scheduling.enabled", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {

    /**
     * Свой планировщик нужен, потому что при виртуальных потоках Boot ставит однопоточный, и одна зависшая из пяти
     * задач с паузой останавливала бы остальные. Размер берётся из {@code spring.task.scheduling.pool.size}, а имя
     * {@code taskScheduler} Spring ищет, когда планировщиков несколько.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }
}
