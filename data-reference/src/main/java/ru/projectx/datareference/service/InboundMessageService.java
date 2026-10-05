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
 * Сохраняет сообщения 1С в том виде, в каком они пришли, и записывает, чем закончилась их обработка.
 *
 * <p>Здесь в одном месте решается, что делать с сообщением дальше. После временной ошибки назначается следующая
 * попытка через задержку из настроенного списка повторов. Постоянная ошибка сразу завершает обработку отказом.
 * Пропуск записывается с причиной. Ни слушатель очереди, ни разбор потока этих решений не принимают, иначе
 * правила в разных местах со временем разойдутся.
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
     * <p>Метод работает в транзакции вызывающего, потому что отметка в журнале входящих и сохранение тела должны
     * фиксироваться вместе. Если сообщение отмечено принятым, но не сохранено, оно потеряно навсегда: брокер его
     * больше не пришлёт, а проверка повторов уже считает его известным.
     */
    @Transactional
    public InboundMessage store(UUID messageId, OneCStream stream, String sourceQueue, String payload) {
        InboundReceipt receipt = new InboundReceipt(messageId, stream, sourceQueue, clock.instant());
        return repository.save(InboundMessage.receive(receipt, payload));
    }

    /**
     * Отмечает, что сообщение разложено по таблицам справочников.
     *
     * <p>Метод работает в транзакции вызывающего, потому что изменения справочников и смена статуса должны
     * фиксироваться вместе. Если статус не запишется, сообщение применят второй раз. Если откатятся изменения,
     * а статус останется, сообщение будет потеряно.
     */
    @Transactional
    public void markProcessed(UUID messageId) {
        require(messageId).markProcessed(clock.instant());
    }

    /**
     * Отмечает, что сообщение не прошло отбор своего потока, и сохраняет причину.
     *
     * <p>Метод работает в транзакции вызывающего: пропуск и изменения, которые разбор успел сделать, должны
     * фиксироваться или откатываться вместе.
     */
    @Transactional
    public void markSkipped(UUID messageId, String reason) {
        require(messageId).markSkipped(reason, clock.instant());
    }

    /**
     * Записывает временную ошибку. Следующая попытка назначается через задержку из настроенного списка повторов.
     * Когда список исчерпан, обработка завершается отказом.
     *
     * <p>Метод открывает собственную транзакцию. Транзакция вызывающего к этому моменту уже откатилась вместе
     * с неудавшимися изменениями, и запись об ошибке в ней не сохранилась бы.
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
     * Записывает постоянную ошибку и сразу завершает обработку отказом. Так бывает, когда сообщение не
     * соответствует контракту: следующая попытка дала бы тот же результат.
     *
     * <p>Метод открывает собственную транзакцию, потому что транзакция вызывающего к этому моменту уже откатилась.
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
