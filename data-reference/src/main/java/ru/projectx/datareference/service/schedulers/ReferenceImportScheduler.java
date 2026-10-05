package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
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
 * <p>Исход каждого сообщения виден в журнале: применено, пропущено с причиной, отложено до повтора или
 * отклонено окончательно. На время разбора в MDC лежат {@code message-id} и поток сообщения, под теми же
 * именами, что при приёме из брокера. Тела сообщения и текста чужого исключения в журнале нет: через этот
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
        for (InboundMessage message : due) {
            importOne(message);
        }
    }

    /** В конце снимаются только ключи сообщения; остальные ключи MDC планировщик не трогает. */
    private void importOne(InboundMessage message) {
        InboundReceipt receipt = message.receipt();
        MDC.put(OneCLogContext.MESSAGE_ID, receipt.messageId().toString());
        MDC.put(OneCLogContext.STREAM, receipt.stream().name());
        try {
            importAndClassify(message);
        }
        finally {
            MDC.remove(OneCLogContext.MESSAGE_ID);
            MDC.remove(OneCLogContext.STREAM);
        }
    }

    private void importAndClassify(InboundMessage message) {
        InboundReceipt receipt = message.receipt();
        try {
            reportOutcome(receipt, importService.importMessage(message));
        }
        catch (RuntimeException failure) {
            // Граница прохода: любая ошибка разбора одного сообщения записывается его исходом, и проход идёт дальше.
            recordFailure(receipt, failures.classify(failure));
        }
    }

    /** Окончательный отказ в журнал пишет сам сервис исходов: только он знает, кончились ли повторы. */
    private void recordFailure(InboundReceipt receipt, ParseFailure failure) {
        switch (failure) {
            case ParseFailure.Temporary temporary -> {
                log.warn("временная ошибка разбора сообщения {} потока {}: {}, SQLSTATE {}",
                        receipt.messageId(), receipt.stream(), temporary.type(), temporary.sqlState());
                inboundMessageService.recordTemporaryFailure(receipt.messageId(), temporary);
            }
            case ParseFailure.Permanent permanent ->
                    inboundMessageService.recordPermanentFailure(receipt.messageId(), permanent);
        }
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
