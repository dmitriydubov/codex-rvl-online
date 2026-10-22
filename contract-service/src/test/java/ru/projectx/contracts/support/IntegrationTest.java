package ru.projectx.contracts.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("no-mutate")
@SpringBootTest(properties = {
        "projectx.scheduling.enabled=false",
        "management.health.brokers.enabled=false",
        "projectx.reference.url=http://reference.test:8080/internal/v1",
        "projectx.brokers.internal.connection.password=no-broker",
        "projectx.brokers.onec.connection.password=no-broker",
        "projectx.brokers.internal.listener-auto-startup=false",
        "projectx.brokers.onec.listener-auto-startup=false"})
@Import({PostgresTestcontainer.class, FixedClockConfig.class})
public @interface IntegrationTest {

    @AliasFor(annotation = SpringBootTest.class)
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;
}
