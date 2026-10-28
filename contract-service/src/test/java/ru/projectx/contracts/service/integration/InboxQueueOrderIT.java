package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.contracts.config.broker.InternalBrokerProperties;
import ru.projectx.contracts.config.broker.OneCBrokerProperties;
import ru.projectx.contracts.repository.InboxMessageRepository;
import ru.projectx.contracts.support.AmqpIntegrationTest;
import ru.projectx.contracts.support.TaskBodies;
import ru.projectx.contracts.support.TestQueues;

@AmqpIntegrationTest
class InboxQueueOrderIT {

    private static final int MESSAGES = 5;
    private static final Duration PAUSE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ALL_WRITTEN_TIMEOUT = Duration.ofSeconds(30);
    private static final String LOCK_NOT_AVAILABLE = "55P03";
    private static final long FAILED_THEN_WRITTEN = 2;

    private static final String PROBE_SCHEMA = "create schema order_probe";
    private static final String ATTEMPTS = "create sequence order_probe.attempts";
    private static final String WRITTEN = """
            create table order_probe.written (
                position   bigserial primary key,
                message_id uuid not null
            )""";
    private static final String FAIL_FIRST_THEN_LOG = """
            create function order_probe.fail_first_then_log() returns trigger
            language plpgsql as $$
            begin
                if new.message_id = tg_argv[0]::uuid then
                    if nextval('order_probe.attempts') = 1 then
                        raise exception 'проверка порядка: отказ записи в журнал' using errcode = 'lock_not_available';
                    end if;
                end if;
                insert into order_probe.written (message_id) values (new.message_id);
                return new;
            end
            $$""";
    private static final String TRIGGER = """
            create trigger order_probe before insert on inbox_messages
            for each row execute function order_probe.fail_first_then_log('%s')""";
    private static final String DROP_TRIGGER = "drop trigger if exists order_probe on inbox_messages";
    private static final String DROP_PROBE_SCHEMA = "drop schema if exists order_probe cascade";
    private static final String WRITTEN_IN_ORDER = "select message_id from order_probe.written order by position";
    private static final String FIRST_MESSAGE_ATTEMPTS = "select last_value from order_probe.attempts";

    @Autowired
    @InternalBroker
    private RabbitTemplate internalTemplate;

    @Autowired
    @InternalBroker
    private TestQueues internalQueues;

    @Autowired
    @OneCBroker
    private TestQueues onecQueues;

    @Autowired
    private InternalBrokerProperties internalBroker;

    @Autowired
    private OneCBrokerProperties onecBroker;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private InboxMessageRepository inbox;

    @Autowired
    private RabbitListenerEndpointRegistry listeners;

    @Autowired
    private InboxConsumerPause pause;

    @BeforeEach
    void emptyJournalAndRunningConsumers() {
        inbox.deleteAll();
        internalQueues.purge(internalBroker.queues().taskChanges());
        await().atMost(PAUSE_TIMEOUT).until(() -> internalQueues.consumers(internalBroker.queues().taskChanges()) == 1);
    }

    @AfterEach
    void removeProbeAndResume() {
        jdbc.sql(DROP_TRIGGER).update();
        jdbc.sql(DROP_PROBE_SCHEMA).update();
        await().atMost(PAUSE_TIMEOUT).until(pause::consumersStopped);
        pause.resumeIfStorageAvailable();
    }

    @Test
    void firstMessageWriteFails_consumersOfBothBrokersPauseAndQueueOrderIsKeptAfterResume() {
        List<UUID> queueOrder = Stream.generate(UUID::randomUUID).limit(MESSAGES).toList();
        failFirstWriteOf(queueOrder.getFirst());

        publishOnOneChannel(queueOrder);

        await().atMost(PAUSE_TIMEOUT).untilAsserted(() -> {
            assertThat(pause.paused()).map(paused -> paused.outage().sqlState()).hasValue(LOCK_NOT_AVAILABLE);
            assertThat(internalQueues.consumers(internalBroker.queues().taskChanges())).isZero();
            assertThat(internalQueues.depth(internalBroker.queues().taskChanges())).isEqualTo(MESSAGES);
            assertThat(onecQueues.consumers(onecBroker.queues().lmPolicyIssues())).isZero();
            assertThat(onecQueues.consumers(onecBroker.queues().kmContractRegistrations())).isZero();
        });
        assertThat(listeners.getListenerContainer(onecBroker.queues().lmPolicyIssues()).isRunning()).isFalse();
        assertThat(writtenInOrder()).isEmpty();

        await().atMost(PAUSE_TIMEOUT).until(pause::consumersStopped);
        pause.resumeIfStorageAvailable();

        await().atMost(ALL_WRITTEN_TIMEOUT)
                .untilAsserted(() -> assertThat(writtenInOrder()).hasSizeGreaterThanOrEqualTo(MESSAGES));
        assertThat(writtenInOrder()).containsExactlyElementsOf(queueOrder);
        assertThat(firstMessageAttempts()).isEqualTo(FAILED_THEN_WRITTEN);
        assertThat(pause.paused()).isEmpty();
        await().atMost(PAUSE_TIMEOUT).untilAsserted(() -> {
            assertThat(internalQueues.consumers(internalBroker.queues().taskChanges())).isEqualTo(1);
            assertThat(onecQueues.consumers(onecBroker.queues().lmPolicyIssues())).isEqualTo(1);
        });
    }

    private void publishOnOneChannel(List<UUID> queueOrder) {
        internalTemplate.invoke(channel -> {
            queueOrder.forEach(messageId -> channel.send("", internalBroker.queues().taskChanges(), task(messageId)));
            return null;
        });
    }

    private static Message task(UUID messageId) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        byte[] body = TaskBodies.upserted().withMetaField("message_id", messageId.toString()).toBytes();
        return MessageBuilder.withBody(body).andProperties(properties).build();
    }

    private void failFirstWriteOf(UUID messageId) {
        Stream.of(PROBE_SCHEMA, ATTEMPTS, WRITTEN, FAIL_FIRST_THEN_LOG, TRIGGER.formatted(messageId))
                .forEach(statement -> jdbc.sql(statement).update());
    }

    private List<UUID> writtenInOrder() {
        return jdbc.sql(WRITTEN_IN_ORDER).query(UUID.class).list();
    }

    private long firstMessageAttempts() {
        return jdbc.sql(FIRST_MESSAGE_ATTEMPTS).query(Long.class).single();
    }
}
