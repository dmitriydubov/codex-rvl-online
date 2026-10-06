package ru.projectx.datareference.service.schedulers;

import java.time.Duration;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import ru.projectx.datareference.config.ImportProperties;

/**
 * Срок хранения тел сообщений 1С:УСК в таблице сырья и в карантине: по истечении срока тело стирается, строка остаётся.
 *
 * <p>Стираются тела полисов и договоров страхования в любом статусе: в них бывают персональные данные (ПДн) —
 * ФИО застрахованного или страхователя-человека. Стираются и тела пропущенных сообщений любого потока: по ним уже
 * ничего не применяется. Тело справочного сообщения в остальных статусах остаётся, по нему сообщение
 * переобрабатывают после починки разбора. Статус и число попыток не стираются; у полиса вместе с телом стирается
 * причина исхода, потому что в ней мог остаться текст ошибки базы со значениями полей. В карантине срок действует
 * на все тела: они не разобраны, и поручиться, что ПДн в них нет, нельзя.
 *
 * <p>Стирание идёт пачками от самых старых строк, каждая пачка — своя короткая транзакция, чтобы не держать
 * блокировки строк, которые читает разбор. Проход продолжается, пока пачки полные.
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

    /** Один проход: стирает пачками все тела сырья и карантина старше их сроков хранения. */
    public void forgetExpiredPayloads() {
        int inbound = eraseInBatches(
                () -> agedMessages.forgetPayloads(properties.payloadTtl(), properties.retentionBatchSize()));
        report(inbound, "сообщений в сырье", properties.payloadTtl());

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
