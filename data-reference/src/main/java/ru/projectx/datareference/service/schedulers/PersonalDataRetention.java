package ru.projectx.datareference.service.schedulers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Срок хранения тела сообщения с персональными данными (ПДн): по истечении срока тело полиса стирается из
 * таблицы сырых сообщений.
 *
 * <p>Полис приходит полным пакетом JSON: ФИО, дата рождения, почта. Сырьё хранится, чтобы проблемное
 * сообщение можно было разобрать глазами, но <b>хранить его вечно значит держать вторую копию реестра
 * застрахованных</b> в таблице, которая для этого не предназначена и живёт по другим правилам доступа.
 *
 * <p>Стирается только тело, сама строка остаётся. По ней по-прежнему видно, что сообщение приходило, когда
 * и чем закончилось.
 *
 * <p><b>Только поток полисов.</b> В остальных потоках ПДн нет, а тело справочного сообщения — то
 * единственное, чем строку можно переобработать, когда починили маппер или наконец пришёл родитель.
 *
 * <p>Стирание идёт пачками, от самых старых строк, и каждая пачка — своя короткая транзакция. Проход идёт
 * рядом с живым разбором, и транзакция на весь остаток держала бы блокировки строк, которые разбор читает.
 * Проход продолжается, пока пачки полные: неполная пачка значит, что остаток исчерпан.
 */
@Component
public class PersonalDataRetention {

    private static final Logger log = LoggerFactory.getLogger(PersonalDataRetention.class);

    private final AgedInboundMessages agedMessages;
    private final TransactionOperations transactions;
    private final ImportProperties properties;

    public PersonalDataRetention(
            AgedInboundMessages agedMessages, TransactionOperations transactions, ImportProperties properties) {

        this.agedMessages = agedMessages;
        this.transactions = transactions;
        this.properties = properties;
    }

    /** Один проход: стирает пачками все тела полисов старше срока хранения. */
    public void forgetExpiredPayloads() {
        int total = 0;
        int erased;
        do {
            erased = forgetOneBatch();
            total += erased;
        }
        while (erased == properties.retentionBatchSize());

        if (total > 0) {
            log.info("стёрты тела {} сообщений полисов старше {}: срок хранения персональных данных",
                    total, properties.payloadTtl());
        }
    }

    private int forgetOneBatch() {
        return transactions.execute(status -> agedMessages.forgetPayloads(
                OneCStream.POLICIES, properties.payloadTtl(), properties.retentionBatchSize()));
    }
}
