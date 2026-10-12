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
 * Приложение целиком на настоящем PostgreSQL с остановленными часами и без брокеров: слушатели и health подключений
 * выключены, иначе health ждал бы несуществующий брокер, а пароли брокеров вымышленные, иначе проверка настроек не
 * дала бы контексту подняться. Мутационное тестирование эти тесты не запускает ({@code no-mutate}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("no-mutate")
@SpringBootTest(properties = {
        "management.health.brokers.enabled=false",
        "projectx.brokers.internal.connection.password=no-broker",
        "projectx.brokers.onec.connection.password=no-broker",
        "projectx.brokers.internal.listener-auto-startup=false",
        "projectx.brokers.onec.listener-auto-startup=false"})
@Import({PostgresTestcontainer.class, FixedClockConfig.class})
public @interface IntegrationTest {

    /**
     * Без настоящего сервера (по умолчанию) — быстрее, и контекст общий у всех таких тестов. Сервер на случайных
     * портах нужен тому, кто проверяет сами порты: актуатор отвечает на своём порту, и без сервера его нет вовсе.
     */
    @AliasFor(annotation = SpringBootTest.class)
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;
}
