package ru.projectx.datareference.service.schedulers;

import java.time.Duration;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import ru.projectx.datareference.config.ImportProperties;

/**
 * Срок хранения тел сообщений с персональными данными (ПДн): по истечении срока тело полиса стирается из
 * таблицы сырых сообщений, а тело карантинного сообщения — из карантина.
 *
 * <p>Полис приходит полным пакетом JSON: ФИО, дата рождения, почта. Сырьё хранится, чтобы проблемное
 * сообщение можно было разобрать глазами, но <b>хранить его вечно значит держать вторую копию реестра
 * застрахованных</b> в таблице, которая для этого не предназначена и живёт по другим правилам доступа.
 * Карантин хранит тела, которые база не приняла в сырьё: они не разобраны, и по имени очереди нельзя
 * поручиться, что ПДн в них нет, поэтому срок действует на весь карантин.
 *
 * <p>Стирается только тело, сама строка остаётся. По ней по-прежнему видно, что сообщение приходило, когда
 * и чем закончилось.
 *
 * <p>В сырье стираются <b>только полисы</b>. В остальных потоках ПДн нет, а тело справочного сообщения — то
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

    /** Один проход: стирает пачками все тела полисов и карантинных сообщений старше их сроков хранения. */
    public void forgetExpiredPayloads() {
        int policies = eraseInBatches(
                () -> agedMessages.forgetPolicyPayloads(properties.payloadTtl(), properties.retentionBatchSize()));
        report(policies, "сообщений полисов", properties.payloadTtl());

        int quarantined = eraseInBatches(
                () -> agedMessages.forgetQuarantineBodies(properties.quarantineTtl(), properties.retentionBatchSize()));
        report(quarantined, "карантинных сообщений", properties.quarantineTtl());
    }

    private int eraseInBatches(IntSupplier batch) {
        int total = 0;
        int erased;
        do {
            erased = transactions.execute(status -> batch.getAsInt());
            total += erased;
        }
        while (erased == properties.retentionBatchSize());
        return total;
    }

    private static void report(int erased, String what, Duration ttl) {
        if (erased > 0) {
            log.info("стёрты тела {} {} старше {}: срок хранения персональных данных", erased, what, ttl);
        }
    }
}
