package ru.projectx.datareference.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Тесту нужны настоящие брокер и база: контейнеры Testcontainers либо внешние
 * {@code TEST_RABBITMQ_URL} и {@code TEST_POSTGRES_URL} вместе. Нет — пропустить,
 * а не уронить. Причина пропуска видна в отчёте JUnit.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@EnabledIf(
        value = "ru.projectx.datareference.support.TestBroker#isAvailable",
        disabledReason = "нет ни Docker, ни пары TEST_RABBITMQ_URL и TEST_POSTGRES_URL — тесты приёма пропущены")
public @interface EnabledIfBrokerAvailable {
}
