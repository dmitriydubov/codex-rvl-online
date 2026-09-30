package ru.projectx.datareference.service.schedulers;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.repository.InboundMessageRepository;

/**
 * Срок хранения тела сообщения с персональными данными.
 *
 * <p>Полис приезжает полным пакетом JSON: ФИО, дата рождения, почта. Сырьё хранится, чтобы
 * проблемное сообщение можно было разобрать глазами, — но <b>хранить его вечно значит
 * держать вторую копию реестра застрахованных</b> в таблице, которая для этого
 * не предназначена и живёт по другим правилам доступа.
 *
 * <p>Стирается только тело; сама строка остаётся. По ней по-прежнему видно, что сообщение
 * приходило, когда и чем закончилось, то есть разбор недоставленных продолжает работать —
 * просто без содержимого.
 *
 * <p><b>Только поток полисов.</b> В остальных потоках персональных данных нет, а тело
 * справочного сообщения — то единственное, чем строку можно переобработать, когда
 * починили маппер или наконец приехал родитель.
 *
 * <p>Пачками, а не одним запросом по всей таблице: чистка идёт по расписанию рядом
 * с живым разбором, и транзакция, заблокировавшая таблицу сырья целиком, остановила бы
 * его. Остаток разберётся следующим проходом — торопиться некуда, порог измеряется
 * сутками.
 */
@Component
public class PersonalDataRetention {

    private static final Logger log = LoggerFactory.getLogger(PersonalDataRetention.class);

    private final AgedInboundMessages agedMessages;
    private final InboundMessageRepository repository;
    private final ImportProperties properties;

    public PersonalDataRetention(
            AgedInboundMessages agedMessages, InboundMessageRepository repository, ImportProperties properties) {

        this.agedMessages = agedMessages;
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${projectx.import.retention-poll-interval:1h}",
            initialDelayString = ImportProperties.INITIAL_DELAY)
    @Transactional
    public void forgetExpiredPayloads() {
        List<InboundMessage> expired = agedMessages.withPayload(
                OneCStream.POLICIES, properties.payloadTtl(), properties.retentionBatchSize());
        if (expired.isEmpty()) {
            return;
        }

        // Обычный цикл, а не стрим: каждый шаг меняет состояние сущности и сохраняет её.
        for (InboundMessage message : expired) {
            message.forgetPayload();
            repository.save(message);
        }
        log.info("стёрты тела {} сообщений полисов старше {}: срок хранения персональных данных",
                expired.size(), properties.payloadTtl());
    }
}
