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
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.OneCLogContext;
import ru.projectx.datareference.service.ParseFailure;
import ru.projectx.datareference.service.ParseFailures;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.ReferenceImportService;

/**
 * Проход по накопленным сырым сообщениям: берёт пачку дозревших и отдаёт каждое в разбор. Что делать с
 * сообщением, знает {@link ReferenceImportService}; здесь — выборка, журнал и запись отказов. Когда
 * запускать проход, задаёт {@link ScheduledPasses}.
 *
 * <p>Отказ записывается тут, а не внутри разбора, из-за транзакций: неудачная попытка откатывает
 * транзакцию разбора вместе с изменениями справочника, и записать отказ можно только снаружи неё.
 * Повторить разбор или сразу отклонить сообщение, решает {@link ParseFailures}: повторяется только
 * временный отказ базы, всё остальное при повторе дало бы тот же результат.
 *
 * <p>Сообщения одного потока 1С:УСК применяются в порядке поступления: версий у них нет, и в справочнике действует
 * последнее полученное. Сообщение, отложенное до повтора, держит более поздние сообщения своего потока, пока не
 * применится или не будет отклонено окончательно, иначе при повторе оно затёрло бы более новые данные. Другие потоки
 * при этом разбираются как обычно. Между проходами это обеспечивает выборка
 * ({@link InboundMessageRepository#findDueForImport}), а внутри прохода — сам проход: сообщение, ушедшее на повтор,
 * держит свой поток до конца прохода.
 *
 * <p>Исход каждого сообщения виден в журнале: применено, пропущено с причиной, отложено до повтора, отклонено
 * окончательно или удержано, пока ждёт повтора более раннее сообщение потока. На время разбора в MDC лежат
 * {@code message-id} и поток сообщения, под теми же именами, что при приёме из брокера. Тела сообщения и текста чужого исключения в журнале нет: через этот
 * путь идут полисы с персональными данными.
 */
@Component
public class ReferenceImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReferenceImportScheduler.class);

    private final InboundMessageRepository repository;
    private final ReferenceImportService importService;
    private final InboundMessageService inboundMessageService;
    private final ParseFailures failures;
    private final ImportProperties properties;
    private final Clock clock;

    public ReferenceImportScheduler(
            InboundMessageRepository repository,
            ReferenceImportService importService,
            InboundMessageService inboundMessageService,
            ParseFailures failures,
            ImportProperties properties,
            Clock clock) {

        this.repository = repository;
        this.importService = importService;
        this.inboundMessageService = inboundMessageService;
        this.failures = failures;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Один проход: взять пачку дозревших сообщений и разобрать каждое.
     *
     * <p>Обычный цикл, а не стрим: каждый шаг пишет в базу и ловит свои исключения, то есть состоит из
     * побочных эффектов.
     */
    public void importPendingMessages() {
        List<InboundMessage> due = repository.findDueForImport(clock.instant(), Limit.of(properties.batchSize()));
        Map<OneCStream, UUID> heldStreams = new EnumMap<>(OneCStream.class);
        for (InboundMessage message : due) {
            importInStreamOrder(message, heldStreams);
        }
    }

    /**
     * Сообщение потока, который в этом проходе уже держит сообщение, отложенное до повтора, не разбирается: при повторе
     * голова потока затёрла бы его более новые данные. Выборка следующего прохода такое сообщение не возьмёт, пока
     * голова ждёт повтора.
     *
     * @param heldStreams потоки, удержанные в этом проходе, и сообщение, которое держит каждый из них
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

    private Optional<InboundStatus> importAndClassify(InboundMessage message) {
        InboundReceipt receipt = message.receipt();
        try {
            reportOutcome(receipt, importService.importMessage(message));
            return Optional.empty();
        }
        catch (RuntimeException failure) {
            // Граница прохода: любая ошибка разбора одного сообщения записывается его исходом, и проход идёт дальше.
            return Optional.of(recordFailure(receipt, failures.classify(failure)));
        }
    }

    /**
     * Окончательный отказ в журнал пишет сам сервис исходов: только он знает, кончились ли повторы.
     *
     * @return статус, с которым сообщение осталось
     */
    private InboundStatus recordFailure(InboundReceipt receipt, ParseFailure failure) {
        return switch (failure) {
            case ParseFailure.Temporary temporary -> {
                log.warn("временная ошибка разбора сообщения {} потока {}: {}, SQLSTATE {}",
                        receipt.messageId(), receipt.stream(), temporary.type(), temporary.sqlState());
                yield inboundMessageService.recordTemporaryFailure(receipt.messageId(), temporary);
            }
            case ParseFailure.Permanent permanent ->
                    inboundMessageService.recordPermanentFailure(receipt.messageId(), permanent);
        };
    }

    /**
     * Причина пропуска пишется в журнал целиком: её составляют правила потоков, и в ней бывают только
     * идентификаторы и признаки справочников, а не данные застрахованных.
     */
    private static void reportOutcome(InboundReceipt receipt, ImportResult result) {
        switch (result) {
            case ImportResult.Applied ignored ->
                    log.info("сообщение {} потока {} применено", receipt.messageId(), receipt.stream());
            case ImportResult.Skipped skipped -> log.info("сообщение {} потока {} пропущено: {}",
                    receipt.messageId(), receipt.stream(), skipped.reason());
        }
    }
}
