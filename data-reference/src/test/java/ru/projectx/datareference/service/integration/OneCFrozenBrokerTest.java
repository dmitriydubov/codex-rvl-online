package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.impl.AMQChannel;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ChannelProxy;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.datareference.config.OneCQueueProperties;
import ru.projectx.datareference.support.AmqpIntegrationTest;
import ru.projectx.datareference.support.FreezableTcpProxy;
import ru.projectx.datareference.support.RabbitTestcontainer;
import ru.projectx.datareference.support.TestBroker;

@AmqpIntegrationTest
@DirtiesContext
class OneCFrozenBrokerTest {

    private static final Duration REPLY_LIMIT = Duration.ofSeconds(2);
    private static final String EXISTING_EXCHANGE = "amq.direct";
    private static final Duration MARGIN = Duration.ofSeconds(5);
    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(30);
    private static final RabbitMQContainer BROKER = startedBroker();
    private static final FreezableTcpProxy PROXY = FreezableTcpProxy.to(BROKER.getHost(), BROKER.getAmqpPort());

    @Autowired
    private OneCQueuePresence presence;

    @Autowired
    private OneCConsumerRestart restart;

    @Autowired
    private OneCQueueConsumers consumers;

    @Autowired
    private RabbitListenerEndpointRegistry listeners;

    @Autowired
    private OneCQueueProperties queues;

    @Autowired
    private CachingConnectionFactory connectionFactory;

    @Autowired
    private OneCQueueContainers containers;

    @DynamicPropertySource
    static void brokerBehindFreezableProxy(DynamicPropertyRegistry registry) {
        registry.add(TestBroker.URL_PROPERTY, () -> "amqp://127.0.0.1:" + PROXY.port());
        registry.add("spring.rabbitmq.username", BROKER::getAdminUsername);
        registry.add("spring.rabbitmq.password", BROKER::getAdminPassword);
        registry.add("RABBITMQ_CHANNEL_RPC_TIMEOUT", () -> REPLY_LIMIT.toSeconds() + "s");
    }

    @AfterAll
    static void stopBroker() throws IOException {
        PROXY.close();
        BROKER.stop();
    }

    @BeforeEach
    void everyConsumerSubscribed() {
        await().atMost(SETTLE_TIMEOUT).untilAsserted(
                () -> assertThat(consumers.queuesWithoutConsumer()).as("очереди без потребителя").isEmpty());
    }

    @Test
    void replyLimit_comesFromItsEnvironmentVariable() {
        assertThat(connectionFactory.getRabbitConnectionFactory().getChannelRpcTimeout())
                .isEqualTo(REPLY_LIMIT.toMillis());
    }

    @Test
    void frozenBroker_failsThePassiveCheckByTheLimit_andItsLateReplyAnswersNoCommandOfAnotherType()
            throws InterruptedException {
        CachingConnectionFactory oneCachedChannel = new CachingConnectionFactory(
                connectionFactory.getRabbitConnectionFactory());
        oneCachedChannel.setAddresses("127.0.0.1:" + PROXY.port());
        try {
            assertLateReplyIgnoredByTheNextCommand(new RabbitTemplate(oneCachedChannel));
        }
        finally {
            oneCachedChannel.destroy();
        }
    }

    private void assertLateReplyIgnoredByTheNextCommand(RabbitTemplate onOneChannel) throws InterruptedException {
        OneCQueuePresence presenceOnOneChannel = new OneCQueuePresence(new StaticListableBeanFactory(
                Map.of("rabbitTemplate", onOneChannel)).getBeanProvider(RabbitTemplate.class));
        assertThat(presenceOnOneChannel.of(queues.policies())).isEqualTo(OneCQueuePresence.OnBroker.PRESENT);
        Channel checkedOn = onOneChannel.execute(OneCFrozenBrokerTest::target);
        CompletableFuture<Channel> nextCommandOn = new CompletableFuture<>();
        PROXY.freeze();
        Thread commanding;
        try {
            assertThat(timed(() -> presenceOnOneChannel.of(queues.policies())))
                    .isEqualTo(OneCQueuePresence.OnBroker.UNREACHABLE);
            commanding = Thread.ofPlatform().start(() -> declareExistingExchange(onOneChannel, nextCommandOn));
            await().atMost(REPLY_LIMIT).until(() -> awaitsBrokerReply(commanding));
        }
        finally {
            PROXY.thaw();
        }
        assertThat(nextCommandOn).succeedsWithin(SETTLE_TIMEOUT).as("следующая команда на том же канале").isSameAs(checkedOn);
        assertThat(commanding.join(SETTLE_TIMEOUT)).isTrue();
    }

    private static void declareExistingExchange(RabbitTemplate onOneChannel, CompletableFuture<Channel> declaredOn) {
        try {
            declaredOn.complete(onOneChannel.execute(channel -> {
                assertThat(channel.exchangeDeclarePassive(EXISTING_EXCHANGE)).isInstanceOf(AMQP.Exchange.DeclareOk.class);
                return target(channel);
            }));
        }
        catch (RuntimeException | AssertionError failed) {
            declaredOn.completeExceptionally(failed);
        }
    }

    private static Channel target(Channel channel) {
        return ((ChannelProxy) channel).getTargetChannel();
    }

    private static boolean awaitsBrokerReply(Thread thread) {
        return thread.getState() == Thread.State.TIMED_WAITING && Arrays.stream(thread.getStackTrace())
                .anyMatch(frame -> frame.getClassName().startsWith(AMQChannel.class.getName()));
    }

    @Test
    void restartPass_givesUpOnAFrozenBrokerByTheLimit_andStartsTheConsumerOnceTheBrokerAnswers() {
        String queue = queues.policies();
        listeners.getListenerContainer(queue).stop();
        PROXY.freeze();
        try {
            timed(() -> {
                restart.restartStopped();
                return queue;
            });
            assertThat(listeners.getListenerContainer(queue).isRunning()).as("потребитель при замёрзшем брокере").isFalse();
        }
        finally {
            PROXY.thaw();
        }
        restart.restartStopped();
        await().atMost(SETTLE_TIMEOUT).untilAsserted(
                () -> assertThat(consumers.queuesWithoutConsumer()).as("очереди без потребителя").isEmpty());
    }

    @Test
    void consumerStartOnAFrozenBroker_failsByTheLimit_andTheContainerRetriesUntilTheBrokerAnswers() {
        String queue = queues.mkb10();
        listeners.getListenerContainer(queue).stop();
        PROXY.freeze();
        try {
            long started = System.nanoTime();
            containers.start(queue);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).as("запуск потребителя при замёрзшем брокере")
                    .isLessThan(REPLY_LIMIT.multipliedBy(2).plus(MARGIN));
            assertThat(listeners.getListenerContainer(queue).isRunning()).as("контейнер повторяет запуск сам").isTrue();
            assertThat(consumers.queuesWithoutConsumer()).as("очереди без потребителя").contains(queue);
        }
        finally {
            PROXY.thaw();
        }
        await().atMost(SETTLE_TIMEOUT).untilAsserted(
                () -> assertThat(consumers.queuesWithoutConsumer()).as("очереди без потребителя").isEmpty());
    }

    private static <T> T timed(Supplier<T> call) {
        long started = System.nanoTime();
        T result = call.get();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).as("ожидание ответа замёрзшего брокера")
                .isBetween(REPLY_LIMIT, REPLY_LIMIT.plus(MARGIN));
        return result;
    }

    private static RabbitMQContainer startedBroker() {
        RabbitMQContainer broker = new RabbitMQContainer(RabbitTestcontainer.IMAGE);
        broker.start();
        return broker;
    }
}
