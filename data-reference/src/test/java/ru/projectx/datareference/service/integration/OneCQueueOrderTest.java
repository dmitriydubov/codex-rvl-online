package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static ru.projectx.datareference.support.OneCMessages.messageWithId;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.datareference.config.OneCQueueProperties;
import ru.projectx.datareference.support.AmqpIntegrationTest;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.RunningListeners;

/**
 * Временный отказ записи сырья на настоящем брокере и настоящей базе: потребители всех очередей встают на
 * паузу, сообщения ждут в очереди, после восстановления принимаются в прежнем порядке.
 *
 * <p>Сервис не сравнивает версии сообщений: применяется последнее записанное. Значит, порядок записи обязан
 * совпадать с порядком очереди и тогда, когда первое сообщение записалось не с первой попытки. Пауза держит
 * порядок сама: неподтверждённое сообщение возвращается в очередь закрытием канала и встаёт на прежнее
 * место, а следующие за ним не выдаются, пока потребитель остановлен. Prefetch 1 здесь по-прежнему нужен:
 * при большем контейнер перед остановкой дорабатывал бы уже выданные сообщения, и они записались бы раньше
 * возвращённого.
 *
 * <p>Отказ — настоящий отказ базы, а не подмена бина: триггер на {@code inbound_message} роняет первую попытку
 * записи первого сообщения кодом {@code lock_not_available} (55P03, так выглядит блокировка дольше
 * {@code lock_timeout}) и протоколирует каждую удавшуюся запись. Счётчик попыток — последовательность:
 * {@code nextval} не откатывается вместе с транзакцией, а строка протокола откатывается — в нём остаются
 * только записи, дошедшие до коммита, в порядке записи. Всё это лежит в отдельной схеме, чтобы не попасть под
 * очистку базы и не пережить тест. Проверка доступности вызывается явно: расписания в тестах выключены.
 *
 * <p>Очереди здесь classic ({@code RabbitTestcontainer}): у них, как и у quorum-очереди с
 * {@code delivery-limit}, возвращённое сообщение встаёт на прежнее место. Quorum-очередь без лимита ставит
 * его в конец — там порядок не держит ничто, и этот тест такой случай не покрывает.
 */
@AmqpIntegrationTest
class OneCQueueOrderTest {

    private static final int MESSAGES = 5;

    /** Запас на пять доставок и остановку контейнеров. */
    private static final Duration PAUSE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ALL_WRITTEN_TIMEOUT = Duration.ofSeconds(30);

    private static final String PROBE_SCHEMA = "create schema order_probe";

    private static final String ATTEMPTS = "create sequence order_probe.attempts";

    private static final String WRITTEN = """
            create table order_probe.written (
                position   bigserial primary key,
                message_id uuid not null
            )""";

    /**
     * Вложенный {@code if}, а не {@code and}: порядок вычисления условий SQL не задан,
     * а счётчик должен считать попытки только первого сообщения.
     */
    private static final String FAIL_FIRST_THEN_LOG = """
            create function order_probe.fail_first_then_log() returns trigger
            language plpgsql as $$
            begin
                if new.message_id = tg_argv[0]::uuid then
                    if nextval('order_probe.attempts') = 1 then
                        raise exception 'проверка порядка: отказ записи сырья' using errcode = 'lock_not_available';
                    end if;
                end if;
                insert into order_probe.written (message_id) values (new.message_id);
                return new;
            end
            $$""";

    private static final String TRIGGER = """
            create trigger order_probe before insert on inbound_message
            for each row execute function order_probe.fail_first_then_log('%s')""";

    private static final String DROP_TRIGGER = "drop trigger if exists order_probe on inbound_message";

    private static final String DROP_PROBE_SCHEMA = "drop schema if exists order_probe cascade";

    private static final String WRITTEN_IN_ORDER = "select message_id from order_probe.written order by position";

    private static final String FIRST_MESSAGE_ATTEMPTS = "select last_value from order_probe.attempts";

    /** Попытки записи первого сообщения: отказ и удачная повторная. */
    private static final long FAILED_THEN_WRITTEN = 2;

    private static final String LOCK_NOT_AVAILABLE = "55P03";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private OneCQueueProperties queues;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private RabbitListenerEndpointRegistry listeners;

    @Autowired
    private OneCConsumerPause pause;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    /** Без работающих слушателей ожидание ниже кончилось бы таймаутом, не назвав причину. */
    @BeforeEach
    void listenersAreRunning() {
        RunningListeners.assertAllRunning(listeners);
    }

    /** Потребители возвращаются и после провала теста: контекст общий с другими тестами приёма. */
    @AfterEach
    void removeProbeAndResume() {
        jdbc.sql(DROP_TRIGGER).update();
        jdbc.sql(DROP_PROBE_SCHEMA).update();
        await().atMost(PAUSE_TIMEOUT).until(pause::consumersStopped);
        pause.resumeIfStorageAvailable();
    }

    @Test
    void firstMessageWriteFails_consumersPauseAndQueueOrderIsKeptAfterResume() {
        List<UUID> queueOrder = Stream.generate(UUID::randomUUID).limit(MESSAGES).toList();
        failFirstWriteOf(queueOrder.getFirst());

        queueOrder.forEach(messageId -> rabbitTemplate.convertAndSend(queues.mkb10(), messageWithId(messageId)));

        await().atMost(PAUSE_TIMEOUT).untilAsserted(() -> {
            assertThat(pause.paused()).map(paused -> paused.outage().sqlState()).hasValue(LOCK_NOT_AVAILABLE);
            assertThat(mkb10Queue().getConsumerCount()).as("потребителей у очереди").isZero();
            assertThat(mkb10Queue().getMessageCount()).as("сообщений ждёт в очереди").isEqualTo(MESSAGES);
        });
        assertThat(listeners.getListenerContainer(queues.policies()).isRunning())
                .as("остановлены потребители всех очередей, не только отказавшей").isFalse();
        assertThat(writtenInOrder()).as("до возобновления ничего не записано").isEmpty();

        await().atMost(PAUSE_TIMEOUT).until(pause::consumersStopped);
        pause.resumeIfStorageAvailable();

        await().atMost(ALL_WRITTEN_TIMEOUT)
                .untilAsserted(() -> assertThat(writtenInOrder()).hasSizeGreaterThanOrEqualTo(MESSAGES));
        assertThat(writtenInOrder()).containsExactlyElementsOf(queueOrder);
        assertThat(firstMessageAttempts()).as("отказ первой записи был внедрён").isEqualTo(FAILED_THEN_WRITTEN);
        assertThat(pause.paused()).isEmpty();
        RunningListeners.assertAllRunning(listeners);
    }

    private QueueInformation mkb10Queue() {
        QueueInformation info = rabbitAdmin.getQueueInfo(queues.mkb10());
        assertThat(info).as("очередь %s объявлена", queues.mkb10()).isNotNull();
        return info;
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
