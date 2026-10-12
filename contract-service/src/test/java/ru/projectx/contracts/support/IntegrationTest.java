package ru.projectx.contracts.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

/**
 * Приложение целиком на настоящем PostgreSQL с остановленными часами и без брокеров: бины подключений есть, но на
 * старте не соединяются, а их компоненты здоровья выключены, иначе health ждал бы несуществующий брокер.
 * Мутационное тестирование эти тесты не запускает (тег {@code no-mutate}), поэтому логику классов покрывают unit-тесты.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("no-mutate")
@SpringBootTest(properties = "management.health.brokers.enabled=false")
@Import({PostgresTestcontainer.class, FixedClockConfig.class})
public @interface IntegrationTest {

    /**
     * Без настоящего сервера (по умолчанию) — быстрее, и контекст общий у всех таких тестов. Сервер на случайных
     * портах нужен тому, кто проверяет сами порты: актуатор отвечает на своём порту, и без сервера его нет вовсе.
     */
    @AliasFor(annotation = SpringBootTest.class)
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;
}
