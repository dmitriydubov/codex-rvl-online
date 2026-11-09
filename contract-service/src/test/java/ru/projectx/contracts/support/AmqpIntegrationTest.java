package ru.projectx.contracts.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("no-mutate")
@SpringBootTest(properties = {
        "projectx.scheduling.enabled=false",
        "projectx.payment.enabled=false",
        "projectx.brokers.internal.connection.tls.enabled=false",
        "projectx.reference.url=http://reference.test:8080/internal/v1",
        "projectx.brokers.onec.connection.tls.enabled=false",
        "projectx.brokers.internal.listener-auto-startup=true",
        "projectx.brokers.onec.listener-auto-startup=true",
        "projectx.storage.endpoint=http://storage.test:9000",
        "projectx.storage.bucket=contracts-test",
        "projectx.storage.access-key=no-storage",
        "projectx.storage.secret-key=no-storage",
        "projectx.leadership.group=${random.uuid}"})
@Import({PostgresTestcontainer.class, RabbitTestcontainer.class, FixedClockConfig.class, LeadershipAwaitConfig.class})
public @interface AmqpIntegrationTest {
}
