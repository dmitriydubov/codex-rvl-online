package ru.projectx.datareference.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

/**
 * Приложение целиком на настоящем PostgreSQL с остановленными часами, без расписаний и брокера: проход разбора менял бы
 * данные чужого теста, а слушатели поднимались бы при перезапуске кэшированного контекста. Мутационное тестирование
 * эти тесты не запускает (тег {@code no-mutate}), поэтому логику классов покрывают unit-тесты.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("no-mutate")
@SpringBootTest(properties = {
        "projectx.scheduling.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"})
@Import({PostgresTestcontainer.class, FixedClockConfig.class, TestSupportConfig.class})
public @interface IntegrationTest {

    /**
     * Без настоящего сервера (по умолчанию) — быстрее, и контекст общий у всех таких тестов. Сервер на случайных
     * портах нужен тому, кто проверяет сами порты: актуатор отвечает на своём порту, и без сервера его нет вовсе.
     */
    @AliasFor(annotation = SpringBootTest.class)
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;
}
