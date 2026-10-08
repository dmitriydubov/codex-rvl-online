package ru.projectx.datareference.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.projectx.datareference.entity.InboundReceipt;

/**
 * Пишет ERROR об окончательном отказе сообщения 1С:УСК, чтобы дежурный разобрал его вручную. В записи только
 * идентификаторы и коды, без исключения: причина остаётся в строке сырья. Запись идёт после фиксации отказа,
 * иначе при откате журнал утверждал бы отказ сообщения, которое разберут снова.
 */
@Component
public class FinalRejectionJournal {

    private static final Logger log = LoggerFactory.getLogger(FinalRejectionJournal.class);

    /** Пишет запись после фиксации текущей транзакции или сразу, если её нет; первая попытка имеет номер 1. */
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
