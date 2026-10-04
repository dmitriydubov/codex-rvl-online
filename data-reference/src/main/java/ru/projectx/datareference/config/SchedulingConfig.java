package ru.projectx.datareference.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Включает работу по расписанию и даёт ей пул потоков.
 *
 * <p>Расписания можно выключить свойством {@code projectx.scheduling.enabled=false}. Это нужно тестам:
 * проход, проснувшийся посреди чужого теста, поменял бы статусы, которые тест проверяет, поэтому тесты
 * вызывают проходы сами. В работающем сервисе свойство не задаётся, и расписания включены.
 *
 * <p>Пул — обычный планировщик Spring с фиксированным числом потоков, размер берётся из
 * {@code spring.task.scheduling.pool.size}. Свой бин нужен потому, что при включённых виртуальных потоках
 * Boot по умолчанию ставит планировщик, который все задачи с паузой между проходами выполняет на одном
 * потоке. У сервиса таких задач четыре: три прохода по расписанию и проверка базы, пока приём из 1С
 * стоит на паузе; зависшая одна останавливала бы остальные.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "projectx.scheduling.enabled", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {

    /**
     * Имя {@code taskScheduler} — то, под которым Spring ищет планировщик для расписаний, если их
     * несколько; сборщик Boot переносит в пул размер, префикс имён потоков и правила остановки из
     * {@code spring.task.scheduling.*}.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }
}
