package ru.projectx.datareference.service.integration;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
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
 * <p>Четыре исхода, и у каждого свой ответ брокеру:
 * <ul>
 *   <li>принято или дубль — {@code ack}: делать больше нечего;</li>
 *   <li>тело не принимает сама база — сообщение уходит в карантин и подтверждается, очередь идёт дальше;</li>
 *   <li>база временно не принимает записи — сообщение остаётся неподтверждённым, потребители очередей
 *       встают на паузу до её восстановления, и брокер отдаст сообщение снова на прежнем месте
 *       (столкновение двух доставок одного сообщения — исключение: пауза и возврат в очередь);</li>
 *   <li>конверт нечитаем — {@code ack}, и сообщение <b>теряется</b>. См. {@link #dropUnidentifiable}.</li>
 * </ul>
 * Что делать после отказа записи, решает {@link OneCStorageFailures}. Сбой самого подтверждения отказом записи
 * не считается: запись уже зафиксирована, и неподтверждённое сообщение брокер пришлёт снова как дубль.
 *
 * <p>На время обработки в MDC лежат поток, очередь и, когда он прочитан, {@code message-id}:
 * по ним строка журнала находит своё сообщение. Имена ключей — {@link OneCLogContext}, общие с разбором
 * по расписанию. Исход каждой доставки считает {@link OneCIngestMetrics}.
 */
@Component
public class OneCQueueListener {

    private static final Logger log = LoggerFactory.getLogger(OneCQueueListener.class);

    private final OneCEnvelopeParser envelopeParser;
    private final OneCIngestService ingestService;
    private final OneCStorageFailures storageFailures;
    private final OneCIngestMetrics metrics;

    public OneCQueueListener(OneCEnvelopeParser envelopeParser, OneCIngestService ingestService,
            OneCStorageFailures storageFailures, OneCIngestMetrics metrics) {

        this.envelopeParser = envelopeParser;
        this.ingestService = ingestService;
        this.storageFailures = storageFailures;
        this.metrics = metrics;
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
     * Подтверждение и счёт исхода стоят вне {@code try} намеренно: ни отказ канала на подтверждении, ни отказ
     * счётчика — не отказ записи. Сырьё уже закоммичено, и повторная запись дала бы дубль, а не потерю.
     */
    private void store(OneCDelivery delivery, UUID messageId) throws IOException {
        IngestOutcome outcome;
        try {
            outcome = ingest(delivery, messageId);
        }
        catch (RuntimeException storageFailure) {
            storageFailures.handle(delivery, messageId, storageFailure);
            return;
        }
        metrics.record(delivery, outcome);
        delivery.acknowledge();
    }

    /** Счёт исхода — снаружи, после записи: отказ счётчика не должен выглядеть отказом базы. */
    private IngestOutcome ingest(OneCDelivery delivery, UUID messageId) {
        if (ingestService.receive(messageId, delivery.stream(), delivery.queue(), delivery.payload())) {
            return IngestOutcome.STORED;
        }
        log.debug("сообщение {} из очереди {} уже принималось — дубль", messageId, delivery.queue());
        return IngestOutcome.DUPLICATE;
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
    private void dropUnidentifiable(OneCDelivery delivery, OneCEnvelopeException cause) throws IOException {
        metrics.record(delivery, IngestOutcome.UNIDENTIFIED);
        log.error("сообщение потока {} из очереди {} потеряно: {} ({} байт). "
                        + "Принять его нельзя — в конверте нет пригодного message-id",
                delivery.stream(), delivery.queue(), cause.getMessage(), delivery.rawBody().length);
        delivery.acknowledge();
    }
}
