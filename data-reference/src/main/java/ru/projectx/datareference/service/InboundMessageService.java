package ru.projectx.datareference.service;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.InboundRetryProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;

/**
 * Хранение сырья из 1С и фиксация исхода его обработки.
 *
 * <p>Здесь собрано решение «что делать с сообщением дальше»: временная ошибка двигает
 * строку по лестнице повторов, постоянная сразу завершает её отказом, а пропуск
 * записывается с причиной. Ни одно из этих решений не должно приниматься в слушателе
 * или в маппере — иначе они разъедутся.
 */
@Service
public class InboundMessageService {

    private final InboundMessageRepository repository;
    private final InboundRetryProperties retryProperties;
    private final Clock clock;

    public InboundMessageService(InboundMessageRepository repository, InboundRetryProperties retryProperties, Clock clock) {
        this.repository = repository;
        this.retryProperties = retryProperties;
        this.clock = clock;
    }

    /**
     * Сохраняет сообщение целиком, со статусом {@code NEW}.
     *
     * <p>Транзакция — вызывающего: запись в журнал входящих и сохранение сырья обязаны
     * коммититься вместе. Сообщение, отмеченное принятым, но не сохранённое, потеряно
     * навсегда — брокер его больше не пришлёт, а дедупликация уже считает его известным.
     */
    @Transactional
    public InboundMessage store(UUID messageId, OneCStream stream, String sourceQueue, String payload) {
        InboundReceipt receipt = new InboundReceipt(messageId, stream, sourceQueue, clock.instant());
        return repository.save(InboundMessage.receive(receipt, payload));
    }

    /**
     * Сообщение разложено по таблицам.
     *
     * <p>Транзакция — вызывающего, и это существенно: изменения справочников и смена
     * статуса обязаны коммититься вместе. Разъединить их значит получить либо сырьё,
     * применённое дважды (статус не записался), либо потерянное (статус записался,
     * изменения откатились).
     */
    @Transactional
    public void markProcessed(UUID messageId) {
        require(messageId).markProcessed(clock.instant());
    }

    /** Сообщение не прошло фильтр потока. Тоже в транзакции вызывающего — см. {@link #markProcessed}. */
    @Transactional
    public void markSkipped(UUID messageId, String reason) {
        require(messageId).markSkipped(reason, clock.instant());
    }

    /**
     * Временная ошибка: назначает следующую попытку по лестнице, а когда она исчерпана —
     * завершает строку отказом.
     *
     * <p>Своя транзакция, отдельная от транзакции вызывающего: к этому моменту она уже
     * откатилась вместе с неудавшимися изменениями, и запись отказа в ней не сохранилась
     * бы.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTemporaryFailure(UUID messageId, String error) {
        InboundMessage message = require(messageId);
        int nextAttempt = message.outcome().attempt() + 1;

        retryProperties.delayBeforeAttempt(nextAttempt).ifPresentOrElse(
                delay -> message.markRetrying(error, clock.instant().plus(delay)),
                () -> message.markFailed("лестница повторов исчерпана на попытке %d: %s"
                        .formatted(nextAttempt, error)));
    }

    /**
     * Постоянная ошибка — невалидное сообщение, чужая схема. Повторять бессмысленно:
     * следующая попытка даст то же самое.
     *
     * <p>Своя транзакция по той же причине, что и у {@link #recordTemporaryFailure}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPermanentFailure(UUID messageId, String error) {
        require(messageId).markFailed(error);
    }

    private InboundMessage require(UUID messageId) {
        return repository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("нет строки сырья для сообщения " + messageId));
    }
}
