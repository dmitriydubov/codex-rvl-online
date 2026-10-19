package ru.projectx.datareference.service.inbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.projectx.datareference.entity.inbound.InboundReceipt;

@Component
public class FinalRejectionJournal {

    private static final Logger log = LoggerFactory.getLogger(FinalRejectionJournal.class);

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
