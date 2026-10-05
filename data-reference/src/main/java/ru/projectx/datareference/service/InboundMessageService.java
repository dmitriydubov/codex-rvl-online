package ru.projectx.datareference.service;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.InboundRetryProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

/**
 * Сохраняет сообщения 1С в том виде, в каком они пришли, и записывает, чем закончилась их обработка.
 *
 * <p>Здесь в одном месте решается, что делать с сообщением дальше. После временного отказа базы назначается
 * следующая попытка через задержку из настроенного списка повторов. Постоянная ошибка сразу завершает обработку
 * отказом. Каждый окончательный отказ разбора виден в журнале записью ERROR.
 * Пропуск записывается с причиной. Отказ, пришедший уже после того, как исход разбора зафиксирован, исход не
 * меняет. Ни слушатель очереди, ни разбор потока этих решений не принимают, иначе
 * правила в разных местах со временем разойдутся. Каждый записанный здесь исход разбора попадает в счётчик
 * переходов статусов ({@link InboundTransitionMetrics}).
 */
@Service
public class InboundMessageService {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageService.class);

    /** Статусы сообщений, которые ещё ждут разбора; у остальных исход разбора зафиксирован. */
    private static final Set<InboundStatus> AWAITING_PARSE = EnumSet.of(InboundStatus.NEW, InboundStatus.RETRY);

    private final InboundMessageRepository repository;
    private final InboundRetryProperties retryProperties;
    private final Clock clock;
    private final InboundTransitionMetrics transitions;
    private final FinalRejectionJournal rejections;

    public InboundMessageService(InboundMessageRepository repository, InboundRetryProperties retryProperties,
            Clock clock, InboundTransitionMetrics transitions, FinalRejectionJournal rejections) {

        this.repository = repository;
        this.retryProperties = retryProperties;
        this.clock = clock;
        this.transitions = transitions;
        this.rejections = rejections;
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
        InboundMessage message = require(messageId);
        message.markProcessed(clock.instant());
        transitions.record(message);
    }

    /**
     * Отмечает, что сообщение не прошло отбор своего потока, и сохраняет причину.
     *
     * <p>Метод работает в транзакции вызывающего: пропуск и изменения, которые разбор успел сделать, должны
     * фиксироваться или откатываться вместе.
     */
    @Transactional
    public void markSkipped(UUID messageId, String reason) {
        InboundMessage message = require(messageId);
        message.markSkipped(reason, clock.instant());
        transitions.record(message);
    }

    /**
     * Записывает временный отказ базы. Следующая попытка назначается через задержку из настроенного списка
     * повторов. Когда список исчерпан, обработка завершается отказом, и он виден в журнале записью ERROR.
     *
     * <p>Метод открывает собственную транзакцию. Транзакция вызывающего к этому моменту уже откатилась вместе
     * с неудавшимися изменениями, и запись об ошибке в ней не сохранилась бы. Если же исход разбора успел
     * зафиксироваться, а отказ пришёл после фиксации, исход остаётся прежним, а в журнал идёт предупреждение.
     *
     * @return статус, с которым сообщение осталось: повтор, отказ или уже зафиксированный исход
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InboundStatus recordTemporaryFailure(UUID messageId, ParseFailure.Temporary failure) {
        InboundMessage message = require(messageId);
        if (isOutcomeCommitted(message)) {
            return keepCommitted(message, failure);
        }
        int failedAttempt = failedAttempt(message);
        retryProperties.delayBeforeAttempt(failedAttempt).ifPresentOrElse(
                delay -> message.markRetrying(failure.reason(), clock.instant().plus(delay)),
                () -> fail(message, "повторы исчерпаны на попытке %d: %s".formatted(failedAttempt, failure.reason()),
                        failure));
        transitions.record(message);
        return message.outcome().status();
    }

    /**
     * Записывает постоянную ошибку и сразу завершает обработку отказом: следующая попытка дала бы тот же
     * результат. Отказ виден в журнале записью ERROR.
     *
     * <p>Метод открывает собственную транзакцию, потому что транзакция вызывающего к этому моменту уже откатилась.
     * Исход, зафиксированный до отказа, остаётся прежним, как и у временного отказа.
     *
     * @return статус, с которым сообщение осталось: отказ или уже зафиксированный исход
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InboundStatus recordPermanentFailure(UUID messageId, ParseFailure.Permanent failure) {
        InboundMessage message = require(messageId);
        if (isOutcomeCommitted(message)) {
            return keepCommitted(message, failure);
        }
        fail(message, failure.reason(), failure);
        transitions.record(message);
        return message.outcome().status();
    }

    /**
     * Все сообщения многочастной передачи {@code correlationId}: сборщику прайсов нужна передача целиком. Читает в
     * транзакции вызывающего — той же, в которой сборщик потом запишет исход передачи.
     */
    @Transactional
    public List<InboundMessage> transferParts(UUID correlationId) {
        return repository.findByCorrelationId(correlationId);
    }

    /**
     * Сохраняет статус, который сборщик прайсов поставил сообщению своей передачи: в буфер, разобрано, пропущено или
     * отказ. Работает в транзакции вызывающего — исход передачи и изменения прайса фиксируются вместе.
     */
    @Transactional
    public void saveTransferPart(InboundMessage message) {
        repository.save(message);
        transitions.record(message);
    }

    /**
     * Исход разбора уже зафиксирован, если сообщение больше не ждёт разбора. Разбор берёт только новые сообщения и
     * сообщения, назначенные на повтор, а неудавшаяся попытка откатывает свой статус вместе с изменениями. Другой
     * статус здесь значит, что попытка зафиксировалась, а отказ пришёл позже: например, соединение оборвалось сразу
     * после фиксации, и приложение не узнало, что база её приняла.
     */
    private static boolean isOutcomeCommitted(InboundMessage message) {
        return !AWAITING_PARSE.contains(message.outcome().status());
    }

    /**
     * Зафиксированный исход не перезаписывается: повтор поверх него разобрал бы сообщение заново, а отказ отправил бы
     * дежурному уже применённое сообщение. Переход в этот исход считается здесь: он зафиксирован, но счётчик
     * переходов увеличивается только после фиксации, о которой транзакция разбора так и не узнала. В журнал идёт тип
     * отказа и SQLSTATE, без текста исключения.
     */
    private InboundStatus keepCommitted(InboundMessage message, ParseFailure failure) {
        InboundReceipt receipt = message.receipt();
        InboundStatus committed = message.outcome().status();
        log.warn("отказ после фиксации исхода сообщения {} потока {}: исход {} сохранён, повтора не будет, отказ {}, "
                + "SQLSTATE {}", receipt.messageId(), receipt.stream(), committed, failure.type(), failure.sqlState());
        transitions.record(message);
        return committed;
    }

    /**
     * Номер попытки, которая сейчас не удалась. Счётчик в строке считает только отложенные попытки, поэтому
     * неудавшаяся — следующая за ними.
     */
    private static int failedAttempt(InboundMessage message) {
        return message.outcome().attempt() + 1;
    }

    /** Окончательный отказ: строка получает причину, а журнал — запись ERROR после фиксации. */
    private void fail(InboundMessage message, String reason, ParseFailure failure) {
        int failedAttempt = failedAttempt(message);
        message.markFailed(reason);
        rejections.rejected(message.receipt(), failedAttempt, failure);
    }

    private InboundMessage require(UUID messageId) {
        return repository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("нет строки сырья для сообщения " + messageId));
    }
}
