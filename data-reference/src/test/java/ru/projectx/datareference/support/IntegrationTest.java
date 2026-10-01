package ru.projectx.datareference.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Тест поднимает приложение целиком на настоящем PostgreSQL, с остановленными часами.
 *
 * <p>Одной аннотацией, а не четырьмя на каждом классе, ради последней строки:
 * <b>шедулер разбора здесь усыплён</b>. Иначе он просыпается посреди чужого теста,
 * разбирает положенное этим тестом сырьё и меняет статусы, которые тест как раз
 * собирался проверить. Тесту самого шедулера это не мешает: он вызывает проход
 * напрямую, что вдобавок делает его детерминированным — без ожиданий и таймаутов.
 * Усыплён и первый тик всех трёх планировщиков сырья: без задержки он случался бы прямо
 * на старте контекста, то есть перед первым тестом класса или посреди него.
 *
 * <p><b>Брокера в этом контексте нет вовсе</b> — автоконфигурация AMQP исключена.
 * Раньше здесь стояло {@code spring.rabbitmq.listener.simple.auto-startup=false}, и этого
 * оказалось недостаточно: {@code RabbitListenerEndpointRegistry.startIfNecessary} стартует
 * контейнер при {@code contextRefreshed || isAutoStartup()}, а флаг {@code contextRefreshed}
 * взводится сразу после первого refresh. Spring останавливает и <b>перезапускает</b>
 * кэшированные контексты между тестовыми классами — и на перезапуске слушатели поднимались
 * вопреки свойству, шли на localhost:5672 и падали на первой же очереди, которой там нет.
 *
 * <p>Отсюда правило: тесту без брокера не нужно выключать слушателей — ему не нужна
 * сама машинерия AMQP. Приём из очереди проверяется отдельно, {@link AmqpIntegrationTest}.
 *
 * <p>Набор свойств у всех тестов один, поэтому контекст Spring между ними
 * переиспользуется, а вместе с контекстом — и контейнер с базой. Отсюда же
 * {@link DatabaseCleaner}: база общая, и каждый тест начинает с пустой.
 *
 * <p>Базу даёт Docker (контейнер) либо {@code TEST_POSTGRES_URL}. Нет ни того, ни другого —
 * тест падает, а не пропускается, см. {@link TestDatabase}.
 *
 * <p><b>Мутационное тестирование эти тесты не запускает</b>: тег {@code no-mutate} они
 * наследуют через мета-аннотацию, а набор gauntlet, если подключён, исключает его через
 * {@code pit.excludedGroups}. Процесс PIT подменяет мутантами класс в одной JVM, а контекст
 * Spring с контейнером поднимается и кэшируется раньше, ещё при поиске тестов. Поэтому мутант
 * в коде, который работает только при сборке контекста, ложно выживает. А мутанты, чьи тесты
 * не уложились в тайм-аут из-за подъёма контекста, уходят в TIMED_OUT, и PIT засчитывает их
 * как убитые.
 *
 * <p>Код, покрытый только этими тестами, PIT считает непокрытым. Логику класса нужно покрыть
 * unit-тестами: мутанты, которых убивали только эти тесты, при касании класса станут
 * непокрытыми или выжившими и уронят mutationGate.
 * {@code ./gradlew test} по-прежнему гоняет эти тесты все.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("no-mutate")
@SpringBootTest(properties = {
        "projectx.import.poll-interval=1h",
        "projectx.import.initial-delay=1h",
        "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"})
@Import({PostgresTestcontainer.class, FixedClockConfig.class, TestSupportConfig.class})
public @interface IntegrationTest {
}
