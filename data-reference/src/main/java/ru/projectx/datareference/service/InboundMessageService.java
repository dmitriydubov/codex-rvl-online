package ru.projectx.datareference.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.InboundRetryProperties;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundOutcome;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

/**
 * Сохраняет сообщения 1С в том виде, в каком они пришли, и записывает, чем закончилась их обработка.
 *
 * <p>Решение о судьбе сообщения принимается только здесь: временный отказ базы ставит повтор по настроенному
 * списку задержек, постоянная ошибка и исчерпанные повторы — окончательный отказ с записью ERROR. Окончательный
 * отказ задевает и сообщения, которые разделяют исход ({@link RejectionScope}). Отказ после фиксации исхода
 * исход не меняет. Каждый записанный исход попадает в счётчик переходов ({@link InboundTransitionMetrics}).
 */
@Service
public class InboundMessageService {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageService.class);

    private final InboundMessageRepository repository;
    private final InboundRetryProperties retryProperties;
    private final Clock clock;
    private final InboundTransitionMetrics transitions;
    private final FinalRejectionJournal rejections;
    private final Map<OneCStream, RejectionScope> scopes;

    /** @param scopes области отказа потоков, у которых отказ разделяют несколько сообщений */
    public InboundMessageService(InboundMessageRepository repository, InboundRetryProperties retryProperties,
            Clock clock, InboundTransitionMetrics transitions, FinalRejectionJournal rejections,
            List<RejectionScope> scopes) {

        this.repository = repository;
        this.retryProperties = retryProperties;
        this.clock = clock;
        this.transitions = transitions;
        this.rejections = rejections;
        this.scopes = scopes.stream()
                .collect(Collectors.toUnmodifiableMap(RejectionScope::stream, Function.identity()));
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
     * Отмечает, что сообщение ждёт записи, на которую ссылается. Работает в транзакции вызывающего, как пропуск.
     *
     * @param record GUID записи справочника, которую меняет сообщение; пусто, если у потока её нет
     */
    @Transactional
    public void markAwaitingParent(UUID messageId, AwaitedParent parent, @Nullable UUID record) {
        InboundMessage message = require(messageId);
        message.markAwaitingParent(parent, record, clock.instant());
        transitions.record(message);
    }

    /**
     * Записывает временный отказ базы: повтор через задержку из настроенного списка, а когда список исчерпан, —
     * окончательный отказ. Транзакция своя: транзакция вызывающего уже откатилась вместе с неудавшимися изменениями.
     *
     * @param beforeAttempt исход сообщения, каким его прочитали до неудавшейся попытки
     * @return статус, с которым сообщение осталось: повтор, отказ или уже зафиксированный исход
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InboundStatus recordTemporaryFailure(UUID messageId, InboundOutcome beforeAttempt,
            ParseFailure.Temporary failure) {

        InboundMessage message = require(messageId);
        if (isOutcomeCommitted(message, beforeAttempt)) {
            return keepCommitted(message, failure);
        }
        int failedAttempt = failedAttempt(message);
        List<InboundMessage> changed = retryProperties.delayBeforeAttempt(failedAttempt)
                .map(delay -> {
                    message.markRetrying(failure.reason(), clock.instant().plus(delay));
                    return List.of(message);
                })
                .orElseGet(() -> fail(message,
                        "повторы исчерпаны на попытке %d: %s".formatted(failedAttempt, failure.reason()), failure));
        changed.forEach(transitions::record);
        return message.outcome().status();
    }

    /**
     * Записывает постоянную ошибку и сразу завершает обработку окончательным отказом. Транзакция своя, как у
     * временного отказа; исход, зафиксированный до отказа, остаётся прежним.
     *
     * @param beforeAttempt исход сообщения, каким его прочитали до неудавшейся попытки
     * @return статус, с которым сообщение осталось: отказ или уже зафиксированный исход
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InboundStatus recordPermanentFailure(UUID messageId, InboundOutcome beforeAttempt,
            ParseFailure.Permanent failure) {

        InboundMessage message = require(messageId);
        if (isOutcomeCommitted(message, beforeAttempt)) {
            return keepCommitted(message, failure);
        }
        fail(message, failure.reason(), failure).forEach(transitions::record);
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
     * Исход разбора уже зафиксирован, если строка сообщения отличается от той, что была до попытки. Неудавшаяся
     * попытка откатывает свой исход вместе с изменениями справочников, и строка остаётся прежней. Другая строка здесь
     * значит, что попытка зафиксировалась, а отказ пришёл позже: например, соединение оборвалось сразу после
     * фиксации, и приложение не узнало, что база её приняла.
     *
     * <p>Сравнивается исход целиком, а не только статус. Сообщение, которое ждало родителя, после пересмотра может
     * снова ждать, уже другого родителя, и статус при этом прежний. Если же пересмотр записал ровно то, что уже было,
     * он ничего не изменил, и повтор безопасен.
     *
     * <p>Оба исхода прочитаны из базы: исход до попытки — выборкой прохода, которая идёт без транзакции, текущий — в
     * своей транзакции этого сервиса, с новым контекстом персистентности. Поэтому копия сообщения, которую разбор менял
     * в памяти, сюда не попадает, и моменты времени сравниваются с одной точностью. Строку, которую между чтением и
     * отказом изменил кто-то другой, например дежурный запросом, сервис тоже сочтёт зафиксированной и не тронет.
     */
    private static boolean isOutcomeCommitted(InboundMessage message, InboundOutcome beforeAttempt) {
        return !message.outcome().equals(beforeAttempt);
    }

    /**
     * Зафиксированный исход не перезаписывается: повтор поверх него разобрал бы сообщение заново, а отказ отправил бы
     * дежурному уже применённое сообщение. Переход в этот исход считается здесь: он зафиксирован, но счётчик
     * переходов увеличивается только после фиксации, о которой транзакция разбора так и не узнала. Если же фиксация
     * прошла, а упало что-то после неё, переход будет посчитан дважды: это редкий случай, и неточность счётчика в нём
     * дешевле, чем потерянный переход в обычном. В журнал идёт тип отказа и SQLSTATE, без текста исключения.
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

    /**
     * Окончательный отказ сообщения и тех, кто разделяет его исход; журнал получает одну запись ERROR — о самом
     * сообщении — после фиксации.
     *
     * @return все сообщения, получившие отказ
     */
    private List<InboundMessage> fail(InboundMessage message, String reason, ParseFailure failure) {
        int failedAttempt = failedAttempt(message);
        List<InboundMessage> rejected = Optional.ofNullable(scopes.get(message.receipt().stream()))
                .map(scope -> scope.reject(message, reason))
                .orElseGet(() -> {
                    message.markFailed(reason);
                    return List.of(message);
                });
        rejections.rejected(message.receipt(), failedAttempt, failure);
        return rejected;
    }

    private InboundMessage require(UUID messageId) {
        return repository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("нет строки сырья для сообщения " + messageId));
    }
}
