package ru.projectx.datareference.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * То же приложение, что в {@link IntegrationTest}, но с настоящим брокером и слушателями; отдельно, чтобы контейнер
 * брокера не замедлял остальные тесты. TLS выключен, а слушатели включены явно, чтобы на тест не влияли переменные
 * окружения запускающего; мутационное тестирование его не запускает (тег {@code no-mutate}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("no-mutate")
@SpringBootTest(properties = {
        "projectx.scheduling.enabled=false",
        "spring.rabbitmq.ssl.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=true"})
@Import({PostgresTestcontainer.class, RabbitTestcontainer.class, FixedClockConfig.class, TestSupportConfig.class})
public @interface AmqpIntegrationTest {
}
