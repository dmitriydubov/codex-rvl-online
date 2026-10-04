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
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.OneCLogContext;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.ReferenceImportService;

/**
 * Проход по накопленным сырым сообщениям: берёт пачку дозревших и отдаёт каждое в разбор. Что делать с
 * сообщением, знает {@link ReferenceImportService}; здесь — выборка, журнал и разбор отказов. Когда
 * запускать проход, задаёт {@link ScheduledPasses}.
 *
 * <p>Классификация отказа живёт тут, а не внутри разбора, из-за транзакций: неудачная попытка откатывает
 * транзакцию разбора вместе с изменениями справочника, и записать отказ можно только снаружи неё.
 * Поэтому здесь ловятся ровно два случая:
 * <ul>
 *   <li>{@link OneCFieldException} — сообщение не соответствует контракту. Ошибка постоянная: следующая
 *       попытка даст то же самое, поэтому строка сразу уходит в окончательный отказ;</li>
 *   <li>любой другой отказ — недоступная база, блокировка, таймаут — временный: строка идёт на лестницу
 *       повторов.</li>
 * </ul>
 *
 * <p>Исход каждого сообщения виден в журнале: применено, пропущено с причиной или отказ. На время разбора в
 * MDC лежат {@code message-id} и поток сообщения, под теми же именами, что при приёме из брокера. Тела
 * сообщения и текста чужого исключения в журнале нет: через этот путь идут полисы с персональными данными.
 */
@Component
public class ReferenceImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReferenceImportScheduler.class);

    private final InboundMessageRepository repository;
    private final ReferenceImportService importService;
    private final InboundMessageService inboundMessageService;
    private final ImportProperties properties;
    private final Clock clock;

    public ReferenceImportScheduler(
            InboundMessageRepository repository,
            ReferenceImportService importService,
            InboundMessageService inboundMessageService,
            ImportProperties properties,
            Clock clock) {

        this.repository = repository;
        this.importService = importService;
        this.inboundMessageService = inboundMessageService;
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
        MDC.put(OneCLogContext.MESSAGE_ID, message.messageId().toString());
        MDC.put(OneCLogContext.STREAM, message.stream().name());
        try {
            importAndClassify(message);
        }
        finally {
            MDC.remove(OneCLogContext.MESSAGE_ID);
            MDC.remove(OneCLogContext.STREAM);
        }
    }

    private void importAndClassify(InboundMessage message) {
        try {
            reportOutcome(message, importService.importMessage(message));
        }
        catch (OneCFieldException permanent) {
            // Текст своего исключения разбора называет поле, но не его значение, поэтому он идёт в журнал.
            log.warn("сообщение {} потока {} не соответствует контракту, повтор бессмыслен: {}",
                    message.messageId(), message.stream(), permanent.getMessage());
            inboundMessageService.recordPermanentFailure(message.messageId(), permanent.getMessage());
        }
        catch (RuntimeException temporary) {
            // В журнал — только тип отказа. Текст чужого исключения способен нести значения полей: ошибка
            // вставки, например, печатает параметры запроса. Полный текст сохраняется в строке сырья, где он
            // под теми же правилами доступа, что и сами данные.
            log.warn("временная ошибка разбора сообщения {} потока {}: {}",
                    message.messageId(), message.stream(), temporary.getClass().getName());
            inboundMessageService.recordTemporaryFailure(message.messageId(), temporary.toString());
        }
    }

    /**
     * Причина пропуска пишется в журнал целиком: её составляют правила потоков, и в ней бывают только
     * идентификаторы и признаки справочников, а не данные застрахованных.
     */
    private static void reportOutcome(InboundMessage message, ImportResult result) {
        switch (result) {
            case ImportResult.Applied ignored ->
                    log.info("сообщение {} потока {} применено", message.messageId(), message.stream());
            case ImportResult.Skipped skipped -> log.info("сообщение {} потока {} пропущено: {}",
                    message.messageId(), message.stream(), skipped.reason());
        }
    }
}
