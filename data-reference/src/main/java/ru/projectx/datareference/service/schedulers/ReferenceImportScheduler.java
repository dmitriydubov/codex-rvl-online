package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundOutcome;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.OneCLogContext;
import ru.projectx.datareference.service.ParseFailure;
import ru.projectx.datareference.service.ParseFailures;
import ru.projectx.datareference.service.onec.ImportOutcome;
import ru.projectx.datareference.service.onec.ReferenceImportService;

/**
 * Проход по накопленным сырым сообщениям: разбор пачки дозревших, журнал исходов и запись отказов. Отказ записывается
 * здесь, потому что неудачная попытка откатывает транзакцию разбора. Тел и текстов чужих исключений в журнале нет:
 * этим путём идут полисы с персональными данными.
 */
@Component
public class ReferenceImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReferenceImportScheduler.class);

    private final InboundMessageRepository repository;
    private final ReferenceImportService importService;
    private final InboundMessageService inboundMessageService;
    private final ParseFailures failures;
    private final OverdueParentWaits overdueParentWaits;
    private final ImportProperties properties;
    private final Clock clock;

    public ReferenceImportScheduler(
            InboundMessageRepository repository,
            ReferenceImportService importService,
            InboundMessageService inboundMessageService,
            ParseFailures failures,
            OverdueParentWaits overdueParentWaits,
            ImportProperties properties,
            Clock clock) {

        this.repository = repository;
        this.importService = importService;
        this.inboundMessageService = inboundMessageService;
        this.failures = failures;
        this.overdueParentWaits = overdueParentWaits;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Просроченные ожидания родителя отклоняются после разбора: сообщение, родитель которого пришёл в этом проходе,
     * срок уже не отклонит, и его возьмёт следующий проход.
     */
    public void importPendingMessages() {
        List<InboundMessage> due = repository.findDueForImport(clock.instant(), Limit.of(properties.batchSize()));
        Map<OneCStream, UUID> heldStreams = new EnumMap<>(OneCStream.class);
        for (InboundMessage message : due) {
            importInStreamOrder(message, heldStreams);
        }
        overdueParentWaits.rejectAndReport();
    }

    /**
     * Поток, голова которого ушла на повтор, до конца прохода не разбирается: версий у сообщений 1С нет, и при повторе
     * голова затёрла бы более новые данные. Между проходами то же правило держит выборка дозревших сообщений.
     */
    private void importInStreamOrder(InboundMessage message, Map<OneCStream, UUID> heldStreams) {
        InboundReceipt receipt = message.receipt();
        Optional.ofNullable(heldStreams.get(receipt.stream())).ifPresentOrElse(
                head -> log.info("сообщение {} потока {} ждёт: раньше него в потоке сообщение {}, отложенное до повтора",
                        receipt.messageId(), receipt.stream(), head),
                () -> holdStreamIfLeftForRetry(message, heldStreams));
    }

    private void holdStreamIfLeftForRetry(InboundMessage message, Map<OneCStream, UUID> heldStreams) {
        InboundReceipt receipt = message.receipt();
        importOne(message)
                .filter(InboundStatus.RETRY::equals)
                .ifPresent(retry -> heldStreams.put(receipt.stream(), receipt.messageId()));
    }

    /**
     * В конце снимаются только ключи сообщения; остальные ключи MDC планировщик не трогает.
     *
     * @return статус, с которым сообщение осталось после отказа, или пусто, если разбор прошёл
     */
    private Optional<InboundStatus> importOne(InboundMessage message) {
        InboundReceipt receipt = message.receipt();
        MDC.put(OneCLogContext.MESSAGE_ID, receipt.messageId().toString());
        MDC.put(OneCLogContext.STREAM, receipt.stream().name());
        try {
            return importAndClassify(message);
        }
        finally {
            MDC.remove(OneCLogContext.MESSAGE_ID);
            MDC.remove(OneCLogContext.STREAM);
        }
    }

    /**
     * Исход сообщения запоминается до разбора: по нему сервис исходов отличит откаченную попытку от зафиксированной,
     * о фиксации которой приложение не узнало.
     */
    private Optional<InboundStatus> importAndClassify(InboundMessage message) {
        InboundReceipt receipt = message.receipt();
        InboundOutcome beforeAttempt = message.outcome();
        try {
            reportOutcome(receipt, importService.importMessage(message));
            return Optional.empty();
        }
        catch (RuntimeException failure) {
            // Граница прохода: любая ошибка разбора одного сообщения записывается его исходом, и проход идёт дальше.
            return Optional.of(recordFailure(receipt, beforeAttempt, failures.classify(failure)));
        }
    }

    /**
     * Окончательный отказ в журнал пишет сам сервис исходов: только он знает, кончились ли повторы.
     *
     * @return статус, с которым сообщение осталось
     */
    private InboundStatus recordFailure(InboundReceipt receipt, InboundOutcome beforeAttempt, ParseFailure failure) {
        return switch (failure) {
            case ParseFailure.Temporary temporary -> {
                log.warn("временная ошибка разбора сообщения {} потока {}: {}, SQLSTATE {}",
                        receipt.messageId(), receipt.stream(), temporary.type(), temporary.sqlState());
                yield inboundMessageService.recordTemporaryFailure(receipt.messageId(), beforeAttempt, temporary);
            }
            case ParseFailure.Permanent permanent ->
                    inboundMessageService.recordPermanentFailure(receipt.messageId(), beforeAttempt, permanent);
        };
    }

    /**
     * Причина пропуска и отказа пишется в журнал целиком: её составляют правила потоков, и в ней бывают только
     * идентификаторы, счётчики и признаки справочников, а не данные застрахованных.
     */
    private static void reportOutcome(InboundReceipt receipt, ImportOutcome outcome) {
        if (outcome.needsAttention()) {
            log.warn("сообщение {} потока {} {}", receipt.messageId(), receipt.stream(), outcome.summary());
            return;
        }
        log.info("сообщение {} потока {} {}", receipt.messageId(), receipt.stream(), outcome.summary());
    }
}
