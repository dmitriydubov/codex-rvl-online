package ru.projectx.datareference.support;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.datareference.config.OneCQueueProperties;

/**
 * Настоящий RabbitMQ для тестов приёма. Мок брокера здесь не годится: проверяется
 * ровно то, чего у мока нет, — подтверждение доставки, возврат сообщения в очередь
 * и повторная доставка.
 *
 * <p>Обычно это контейнер Testcontainers: адрес и учётные данные подставляет в контекст
 * {@link ServiceConnection}. Если задан {@code TEST_RABBITMQ_URL}, контейнер не поднимается,
 * а соединение идёт на внешний брокер — для машин без Docker, см. {@link TestBroker}.
 *
 * <p>Очереди объявляет тест, а не приложение: на контуре они заведены со стороны 1С:УСК,
 * и сервис их не создаёт ({@code spring.rabbitmq.dynamic: false}). Здесь тот же
 * порядок — сначала очереди на брокере, потом потребители.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestcontainer {

    /**
     * Та же версия, что у брокера 1С:УСК на тестовом и продовом контуре (RabbitMQ 4.1.2,
     * Erlang 27.3.4.2): поведение подтверждений и возврата в очередь проверяется на ней,
     * а не на последней 4.1.x, которую принёс бы плавающий тег.
     */
    private static final String IMAGE = "rabbitmq:4.1.2-alpine";

    @Bean
    @ServiceConnection
    @ConditionalOnExpression("'${" + TestBroker.URL_PROPERTY + ":}'.isEmpty()")
    public RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(IMAGE);
    }

    /**
     * Адрес в виде {@code amqp://user:password@host:port/vhost} — Boot разбирает из него
     * и хост с портом, и учётные данные, и виртуальный хост.
     */
    @Bean
    @ConditionalOnExpression("!'${" + TestBroker.URL_PROPERTY + ":}'.isEmpty()")
    public DynamicPropertyRegistrar externalBroker(Environment environment) {
        String url = environment.getRequiredProperty(TestBroker.URL_PROPERTY);
        return registry -> registry.add("spring.rabbitmq.addresses", () -> url);
    }

    /**
     * {@code RabbitAdmin} объявляет очереди на первом же соединении — то есть раньше,
     * чем потребители начнут к ним подключаться.
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    /**
     * Все очереди контура — перечислены целиком намеренно. Забытая очередь означает,
     * что потребитель не найдёт её на брокере и остановится
     * ({@code missing-queues-fatal: true}), то есть новый поток без своей очереди
     * в тесте молча не проедет.
     */
    @Bean
    public Declarables oneCQueues(OneCQueueProperties queues) {
        List<Declarable> declarations = Stream.of(
                        queues.policies(), queues.insuranceContracts(), queues.contragents(),
                        queues.mkb10(), queues.lpuContracts(),
                        queues.programs(), queues.appealTypes(), queues.lpuContacts(), queues.priceLists(),
                        queues.priceListsFinal(), queues.contragentServices())
                .<Declarable>map(name -> QueueBuilder.durable(name).build())
                .toList();
        return new Declarables(declarations);
    }
}
