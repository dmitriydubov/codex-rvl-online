package ru.projectx.datareference.service.integration;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.IngestProperties;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCEnvelopeException;
import ru.projectx.datareference.service.OneCLogContext;
import ru.projectx.datareference.service.onec.OneCIngestService;

/**
 * Вход из брокера 1С:УСК. Какая очередь какому потоку принадлежит, задаёт {@link OneCQueue},
 * точки приёма регистрирует {@link OneCQueueEndpoints}; сюда доставка приходит уже с потоком.
 *
 * <p>Подтверждение брокеру уходит <b>только после</b> того, как приём зафиксирован
 * в базе. При автоматическом подтверждении сообщение считалось бы обработанным
 * в момент доставки, и падение до коммита теряло бы его молча.
 *
 * <p>Три исхода, и у каждого своё подтверждение:
 * <ul>
 *   <li>принято или дубль — {@code ack}: делать больше нечего;</li>
 *   <li>наша база недоступна — {@code nack} с возвратом в очередь после паузы: сообщение
 *       не наше дело терять, брокер отдаст его снова, когда база вернётся;</li>
 *   <li>конверт нечитаем — {@code ack}, и сообщение <b>теряется</b>. См.
 *       {@link #dropUnidentifiable}.</li>
 * </ul>
 *
 * <p>На время обработки в MDC лежат поток, очередь и, когда он прочитан, {@code message-id}:
 * по ним строка журнала находит своё сообщение. Имена ключей — {@link OneCLogContext}, общие с разбором
 * по расписанию.
 */
@Component
public class OneCQueueListener {

    private static final Logger log = LoggerFactory.getLogger(OneCQueueListener.class);

    private final OneCEnvelopeParser envelopeParser;
    private final OneCIngestService ingestService;
    private final IngestProperties properties;

    public OneCQueueListener(
            OneCEnvelopeParser envelopeParser, OneCIngestService ingestService, IngestProperties properties) {

        this.envelopeParser = envelopeParser;
        this.ingestService = ingestService;
        this.properties = properties;
    }

    /** Принимает одно сообщение очереди потока {@code stream} и отвечает брокеру. */
    public void receive(OneCStream stream, Message message, Channel channel) throws IOException {
        OneCDelivery delivery = new OneCDelivery(stream, message, channel);
        MDC.put(OneCLogContext.STREAM, stream.name());
        MDC.put(OneCLogContext.QUEUE, delivery.queue());
        try {
            accept(delivery);
        }
        finally {
            MDC.remove(OneCLogContext.STREAM);
            MDC.remove(OneCLogContext.QUEUE);
        }
    }

    private void accept(OneCDelivery delivery) throws IOException {
        UUID messageId;
        try {
            messageId = envelopeParser.messageIdOf(delivery.body());
        }
        catch (OneCEnvelopeException unidentifiable) {
            dropUnidentifiable(delivery, unidentifiable);
            return;
        }
        MDC.put(OneCLogContext.MESSAGE_ID, messageId.toString());
        try {
            store(delivery, messageId);
        }
        finally {
            MDC.remove(OneCLogContext.MESSAGE_ID);
        }
    }

    /**
     * Возврат в очередь здесь ничем не ограничен, и это не недосмотр: сохранение —
     * запись байтов, которые только что разобрались как JSON, и постоянная ошибка у неё
     * известна одна: тело, которое не принимает колонка {@code jsonb} (например, с нулевым
     * символом U+0000). Такое тело пока возвращается в очередь снова и снова; отдельная
     * обработка для него запланирована. Недоступная база проходит сама. Повтор
     * не превращается в горячий цикл из-за паузы перед возвратом.
     */
    private void store(OneCDelivery delivery, UUID messageId) throws IOException {
        try {
            boolean stored = ingestService.receive(messageId, delivery.stream(), delivery.queue(), delivery.payload());
            if (!stored) {
                log.debug("сообщение {} из очереди {} уже принималось — дубль", messageId, delivery.queue());
            }
            delivery.acknowledge();
        }
        catch (RuntimeException storageUnavailable) {
            // Тип, но не текст: сообщение об ошибке вставки печатает параметры запроса,
            // а этим путём проходят полисы с персональными данными.
            log.warn("сырьё сообщения {} не сохранено, вернём источнику через {}: {}",
                    messageId, properties.storageUnavailableBackoff(),
                    storageUnavailable.getClass().getName());
            pauseBeforeRedelivery();
            delivery.returnToQueue();
        }
    }

    /**
     * Сообщение, у которого не читается {@code message-id}, принять невозможно: это ключ
     * и дедупликации, и строки сырья. Оно подтверждается и <b>теряется</b>.
     *
     * <p>Так лучше двух других вариантов. Вернуть в очередь — получить бесконечную
     * передоставку одного и того же: источник пришлёт то же самое. Сохранить под
     * придуманным идентификатором — нарушить смысл ключа и получить повторное сохранение
     * при каждой передоставке.
     *
     * <p>Единственный след — эта запись в логе, поэтому она {@code ERROR} и говорит прямо,
     * что сообщение потеряно. Тело в лог не идёт: у нечитаемого конверта внутри вполне
     * может оказаться полис. Случай почти наверняка означает поломку контракта или чужую
     * очередь, то есть касается не одного сообщения, а всех.
     */
    private static void dropUnidentifiable(OneCDelivery delivery, OneCEnvelopeException cause) throws IOException {
        log.error("сообщение потока {} из очереди {} потеряно: {} ({} байт). "
                        + "Принять его нельзя — в конверте нет пригодного message-id",
                delivery.stream(), delivery.queue(), cause.getMessage(), delivery.body().length);
        delivery.acknowledge();
    }

    /**
     * Держит паузу перед возвратом сообщения. Прерывание — это остановка контейнера:
     * флаг восстанавливаем и возвращаем сообщение сразу, канал ещё открыт.
     */
    private void pauseBeforeRedelivery() {
        try {
            Thread.sleep(properties.storageUnavailableBackoff());
        }
        catch (InterruptedException shuttingDown) {
            Thread.currentThread().interrupt();
        }
    }
}
