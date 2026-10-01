package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.datareference.config.OneCQueueProperties;
import ru.projectx.datareference.support.AmqpIntegrationTest;
import ru.projectx.datareference.support.DatabaseCleaner;

/**
 * Порядок записи сырья, когда запись одного сообщения не удалась, — на настоящем брокере
 * и настоящей базе.
 *
 * <p>Сервис не сравнивает версии сообщений: применяется последнее записанное. Значит, порядок
 * записи обязан совпадать с порядком очереди и тогда, когда сообщение вернулось в неё после
 * отказа базы. Возвращённое брокер ставит на прежнее место в очереди, но сообщения, уже выданные
 * потребителю сверх него (prefetch), до потребителя доехали раньше, и при prefetch больше 1 они
 * записываются первыми: зонд на prefetch 20 дал порядок [2, 3, 4, 5, 1]. Поэтому prefetch — 1,
 * и этот тест его держит.
 *
 * <p>Отказ — настоящий отказ базы, а не подмена бина: триггер на {@code inbound_message} роняет
 * первую попытку записи первого сообщения и протоколирует каждую удавшуюся запись. Счётчик
 * попыток — последовательность: {@code nextval} не откатывается вместе с транзакцией, а строка
 * протокола откатывается — в нём остаются только записи, дошедшие до коммита, в порядке записи.
 * Всё это лежит в отдельной схеме, чтобы не попасть под очистку базы и не пережить тест.
 */
@AmqpIntegrationTest
class OneCQueueOrderTest {

    private static final int MESSAGES = 5;

    /**
     * Запас на пять доставок, паузу перед возвратом сообщения (умолчание
     * {@code storage-unavailable-backoff} — 5 с) и повторную доставку.
     */
    private static final Duration ALL_WRITTEN_TIMEOUT = Duration.ofSeconds(30);

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
                if new.message_id = tg_argv[0]::uuid and nextval('order_probe.attempts') = 1 then
                    raise exception 'проверка порядка: отказ записи сырья';
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

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OneCQueueProperties queues;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @AfterEach
    void removeProbe() {
        jdbc.sql(DROP_TRIGGER).update();
        jdbc.sql(DROP_PROBE_SCHEMA).update();
    }

    @Test
    void firstMessageWriteFails_queueOrderIsKept() {
        List<UUID> queueOrder = Stream.generate(UUID::randomUUID).limit(MESSAGES).toList();
        failFirstWriteOf(queueOrder.getFirst());

        queueOrder.forEach(messageId -> rabbitTemplate.convertAndSend(queues.mkb10(), envelope(messageId)));

        await().atMost(ALL_WRITTEN_TIMEOUT).until(() -> writtenInOrder().size() == MESSAGES);
        assertThat(writtenInOrder()).containsExactlyElementsOf(queueOrder);
    }

    private void failFirstWriteOf(UUID messageId) {
        Stream.of(PROBE_SCHEMA, ATTEMPTS, WRITTEN, FAIL_FIRST_THEN_LOG, TRIGGER.formatted(messageId))
                .forEach(statement -> jdbc.sql(statement).update());
    }

    private List<UUID> writtenInOrder() {
        return jdbc.sql(WRITTEN_IN_ORDER).query(UUID.class).list();
    }

    private static String envelope(UUID messageId) {
        return """
                {"data":{"guid":"1a2b3c4d-0000-4000-8000-000000000001","code":"A00"},\
                "metadata":{"message-id":"%s","topic":"","producer":"1c-usk"}}"""
                .formatted(messageId);
    }
}
