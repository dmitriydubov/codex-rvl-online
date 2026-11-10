package ru.projectx.contracts.service.payment.order;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.service.outbox.OutboxJournal;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class DocumentFixes {

    private final DocumentFixMessages messages;
    private final OutboxJournal journal;

    public DocumentFixes(DocumentFixMessages messages, OutboxJournal journal) {
        this.messages = messages;
        this.journal = journal;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Contract contract, PaymentOrder order, Instant now) {
        journal.record(messages.draft(contract, order, now), now);
    }
}
