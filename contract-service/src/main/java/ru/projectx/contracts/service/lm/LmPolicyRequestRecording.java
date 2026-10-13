package ru.projectx.contracts.service.lm;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.entity.PersonSnapshot;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.repository.ContractPersonRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;

@Service
public class LmPolicyRequestRecording {

    static final String AGGREGATE_TYPE = "LM_POLICY";

    private final LmPolicyRepository policies;
    private final ContractPersonRepository persons;
    private final LmPolicyRequestMessages messages;
    private final OutboxJournal journal;
    private final RequestStatusPublication statuses;
    private final Clock clock;

    public LmPolicyRequestRecording(LmPolicyRepository policies, ContractPersonRepository persons,
            LmPolicyRequestMessages messages, OutboxJournal journal, RequestStatusPublication statuses, Clock clock) {
        this.policies = policies;
        this.persons = persons;
        this.messages = messages;
        this.journal = journal;
        this.statuses = statuses;
        this.clock = clock;
    }

    @Transactional
    public void submit(UUID policyId, ExternalIdentifiers identifiers) {
        LmPolicy policy = load(policyId);
        if (policy.status() != LmPolicyStatus.NEW) {
            return;
        }
        Instant now = clock.instant();
        policy.submit(now);
        MessageMeta meta = messages.meta(policy, now);
        String payload = messages.write(meta, policy, insuredOf(policy), identifiers);
        MessageEnvelope envelope = new MessageEnvelope(meta.messageId(), meta.correlationId(), meta.topic(),
                meta.eventType(), meta.entityVersion());
        journal.record(new OutboxDraft(AGGREGATE_TYPE, policy.id(), envelope, payload), now);
    }

    @Transactional
    public void refuse(UUID policyId, IssueError error) {
        LmPolicy policy = load(policyId);
        if (policy.status() != LmPolicyStatus.NEW) {
            return;
        }
        Instant now = clock.instant();
        IssuanceSnapshot before = statuses.watch(policy.state().request().individualRequestId());
        policy.markError(error, now);
        statuses.afterRecordChange(before, now);
    }

    private PersonSnapshot insuredOf(LmPolicy policy) {
        return persons.findByLmPolicyIdOrderByRole(policy.id()).stream()
                .filter(person -> person.role() == PersonRole.INSURED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("у полиса ЛМ " + policy.id() + " нет снимка застрахованного"))
                .person();
    }

    private LmPolicy load(UUID policyId) {
        return policies.findById(policyId)
                .orElseThrow(() -> new IllegalStateException("полиса ЛМ " + policyId + " нет"));
    }
}
