package ru.projectx.datareference.service.integration;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.IngestProperties;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCEnvelopeException;
import ru.projectx.datareference.service.onec.OneCIngestService;

/**
 * Вход из брокера 1С:УСК. По методу на очередь — так связь «очередь → поток» видно
 * глазами, а не через таблицу соответствий, которую надо держать в голове.
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

    @RabbitListener(queues = "${projectx.onec.queues.policies}")
    public void onPolicies(Message message, Channel channel) throws IOException {
        receive(OneCStream.POLICIES, message, channel);
    }

    @RabbitListener(queues = "${projectx.onec.queues.insurance-contracts}")
    public void onInsuranceContracts(Message message, Channel channel) throws IOException {
        receive(OneCStream.INSURANCE_CONTRACTS, message, channel);
    }

    @RabbitListener(queues = "${projectx.onec.queues.contragents}")
    public void onContragents(Message message, Channel channel) throws IOException {
        receive(OneCStream.CONTRAGENTS, message, channel);
    }

    @RabbitListener(queues = "${projectx.onec.queues.mkb10}")
    public void onMkb10(Message message, Channel channel) throws IOException {
        receive(OneCStream.MKB10, message, channel);
    }

    @RabbitListener(queues = "${projectx.onec.queues.lpu-contracts}")
    public void onLpuContracts(Message message, Channel channel) throws IOException {
        receive(OneCStream.LPU_CONTRACTS, message, channel);
    }

    @RabbitListener(queues = "${projectx.onec.queues.programs}")
    public void onPrograms(Message message, Channel channel) throws IOException {
        receive(OneCStream.PROGRAMS, message, channel);
    }

    @RabbitListener(queues = "${projectx.onec.queues.appeal-types}")
    public void onAppealTypes(Message message, Channel channel) throws IOException {
        receive(OneCStream.APPEAL_TYPES, message, channel);
    }

    @RabbitListener(queues = "${projectx.onec.queues.lpu-contacts}")
    public void onLpuContacts(Message message, Channel channel) throws IOException {
        receive(OneCStream.LPU_CONTACTS, message, channel);
    }

    /** Пакет строк прайса. Применяется не поодиночке, а в составе собранной передачи. */
    @RabbitListener(queues = "${projectx.onec.queues.price-lists}")
    public void onPriceListBatch(Message message, Channel channel) throws IOException {
        receive(OneCStream.PRICE_LISTS, message, channel);
    }

    /** Финальное сообщение передачи прайса — то, что несёт счётчики. Поток тот же. */
    @RabbitListener(queues = "${projectx.onec.queues.price-lists-final}")
    public void onPriceListFinal(Message message, Channel channel) throws IOException {
        receive(OneCStream.PRICE_LISTS, message, channel);
    }

    @RabbitListener(queues = "${projectx.onec.queues.contragent-services}")
    public void onContragentServices(Message message, Channel channel) throws IOException {
        receive(OneCStream.CONTRAGENT_SERVICES, message, channel);
    }

    private void receive(OneCStream stream, Message message, Channel channel) throws IOException {
        String queue = message.getMessageProperties().getConsumerQueue();

        UUID messageId;
        try {
            messageId = envelopeParser.messageIdOf(message.getBody());
        }
        catch (OneCEnvelopeException unidentifiable) {
            dropUnidentifiable(stream, queue, message, channel, unidentifiable);
            return;
        }
        store(stream, queue, messageId, message, channel);
    }

    /**
     * Возврат в очередь здесь ничем не ограничен, и это не недосмотр: сохранение —
     * запись байтов, которые только что разобрались как JSON, и постоянной ошибки
     * у неё быть не может. Недоступная база — может, и она проходит сама. Повтор
     * не превращается в горячий цикл из-за паузы перед возвратом.
     */
    private void store(OneCStream stream, String queue, UUID messageId, Message message, Channel channel)
            throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            boolean stored = ingestService.receive(
                    messageId, stream, queue, new String(message.getBody(), StandardCharsets.UTF_8));
            if (!stored) {
                log.debug("сообщение {} из очереди {} уже принималось — дубль", messageId, queue);
            }
            channel.basicAck(deliveryTag, false);
        }
        catch (RuntimeException storageUnavailable) {
            // Тип, но не текст: сообщение об ошибке вставки печатает параметры запроса,
            // а этим путём проходят полисы с персональными данными.
            log.warn("сырьё сообщения {} не сохранено, вернём источнику через {}: {}",
                    messageId, properties.storageUnavailableBackoff(),
                    storageUnavailable.getClass().getName());
            pauseBeforeRedelivery();
            channel.basicNack(deliveryTag, false, true);
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
    private void dropUnidentifiable(
            OneCStream stream, String queue, Message message, Channel channel, OneCEnvelopeException cause)
            throws IOException {

        log.error("сообщение потока {} из очереди {} потеряно: {} ({} байт). "
                        + "Принять его нельзя — в конверте нет пригодного message-id",
                stream, queue, cause.getMessage(), message.getBody().length);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
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
