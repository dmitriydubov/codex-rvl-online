package ru.projectx.datareference.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Тесту нужна настоящая база: контейнер Testcontainers либо внешний PostgreSQL
 * из {@code TEST_POSTGRES_URL}. Нет ни того, ни другого — пропустить, а не уронить.
 * Причина пропуска видна в отчёте JUnit.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@EnabledIf(
        value = "ru.projectx.datareference.support.TestDatabase#isAvailable",
        disabledReason = "нет ни Docker, ни TEST_POSTGRES_URL — тесты с базой пропущены")
public @interface EnabledIfDatabaseAvailable {
}
