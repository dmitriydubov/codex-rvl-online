package ru.projectx.datareference.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.projectx.datareference.entity.InboundReceipt;

/**
 * Запись уровня ERROR об окончательном отказе сообщения 1С:УСК: по ней дежурный узнаёт, что сообщение больше не
 * повторят и его нужно разобрать вручную.
 *
 * <p>В записи только идентификаторы и коды: {@code message-id}, поток, номер неудачной попытки, тип ошибки и
 * SQLSTATE. Причина остаётся в строке сырья, где она под теми же правилами доступа, что и само сообщение.
 * Исключение к записи не прикладывается.
 *
 * <p>Запись делается после фиксации транзакции, в которой записан отказ. Если фиксация не удалась, строка
 * осталась прежней и сообщение разберут снова, а журнал не должен утверждать обратное.
 */
@Component
public class FinalRejectionJournal {

    private static final Logger log = LoggerFactory.getLogger(FinalRejectionJournal.class);

    /**
     * Сообщает об окончательном отказе после фиксации текущей транзакции или сразу, если транзакции нет.
     *
     * @param failedAttempt номер неудавшейся попытки разбора, первая попытка — 1
     */
    public void rejected(InboundReceipt receipt, int failedAttempt, ParseFailure failure) {
        Runnable report = () -> log.error(
                "сообщение {} потока {} отклонено окончательно на попытке {}: {}, SQLSTATE {}",
                receipt.messageId(), receipt.stream(), failedAttempt, failure.type(), failure.sqlState());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            report.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new ReportAfterCommit(report));
    }

    private record ReportAfterCommit(Runnable report) implements TransactionSynchronization {

        @Override
        public void afterCommit() {
            report.run();
        }
    }
}
