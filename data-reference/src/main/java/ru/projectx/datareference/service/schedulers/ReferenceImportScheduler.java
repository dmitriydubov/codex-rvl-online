package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.onec.ReferenceImportService;

/**
 * Когда разбирать накопленное сырьё. Что при этом делать — знает
 * {@link ReferenceImportService}; здесь только расписание и разбор отказов.
 *
 * <p>Классификация отказа живёт тут, а не внутри разбора, по причине транзакций:
 * неудачная попытка откатывает транзакцию разбора вместе с изменениями справочника,
 * и записать отказ можно только снаружи неё. Отсюда же и правило — <b>ловим ровно два
 * случая</b>:
 * <ul>
 *   <li>{@link OneCFieldException} — сообщение не соответствует контракту. Ошибка
 *       постоянная: следующая попытка даст то же самое, поэтому строка сразу уходит
 *       в терминальный отказ;</li>
 *   <li>любой другой отказ — недоступная база, блокировка, таймаут — временный:
 *       строка идёт на лестницу повторов.</li>
 * </ul>
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
     * <p>Обычный цикл, а не стрим: каждый шаг пишет в базу и ловит свои исключения —
     * это побочные эффекты по определению, и стрим здесь только притворялся бы
     * декларативностью.
     */
    @Scheduled(fixedDelayString = "${projectx.import.poll-interval:10s}")
    public void importPendingMessages() {
        List<InboundMessage> due = repository.findDueForImport(clock.instant(), Limit.of(properties.batchSize()));
        for (InboundMessage message : due) {
            importOne(message);
        }
    }

    private void importOne(InboundMessage message) {
        try {
            importService.importMessage(message);
        }
        catch (OneCFieldException permanent) {
            log.warn("сообщение {} потока {} не соответствует контракту, повтор бессмыслен: {}",
                    message.messageId(), message.stream(), permanent.getMessage());
            inboundMessageService.recordPermanentFailure(message.messageId(), permanent.getMessage());
        }
        catch (RuntimeException temporary) {
            // В лог — только тип отказа. Текст чужого исключения способен нести значения
            // полей: ошибка вставки, например, печатает параметры запроса, а через этот
            // же путь проходят полисы с персональными данными. Полный текст сохраняется
            // в строке сырья — там он под теми же правилами доступа, что и сами данные.
            log.warn("временная ошибка разбора сообщения {} потока {}: {}",
                    message.messageId(), message.stream(), temporary.getClass().getName());
            inboundMessageService.recordTemporaryFailure(message.messageId(), temporary.toString());
        }
    }
}
