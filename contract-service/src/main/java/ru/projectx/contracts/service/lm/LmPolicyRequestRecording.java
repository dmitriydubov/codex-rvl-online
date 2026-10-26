package ru.projectx.contracts.service.lm;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.entity.PersonSnapshot;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.repository.ContractPersonRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.UskRequestRecording;
import ru.projectx.contracts.service.usk.UskSubmission;

@Service
public class LmPolicyRequestRecording extends UskRequestRecording<LmPolicy, LmPolicyStatus> {

    private final ContractPersonRepository persons;
    private final LmPolicyRequestMessages messages;

    public LmPolicyRequestRecording(LmPolicyRepository policies, ContractPersonRepository persons,
            LmPolicyRequestMessages messages, OutboxJournal journal, RequestStatusPublication statuses, Clock clock) {
        super(LmPolicyKind.KIND, policies, journal, statuses, clock);
        this.persons = persons;
        this.messages = messages;
    }

    @Override
    protected UskSubmission compose(LmPolicy policy, ExternalIdentifiers identifiers, Instant now) {
        MessageMeta meta = messages.meta(policy, now);
        String payload = messages.write(meta, policy, insuredOf(policy), identifiers);
        return new UskSubmission(draft(policy, meta, payload), List.of());
    }

    private PersonSnapshot insuredOf(LmPolicy policy) {
        return persons.findByLmPolicyIdOrderByRole(policy.id()).stream()
                .filter(person -> person.role() == PersonRole.INSURED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("у полиса ЛМ " + policy.id() + " нет снимка застрахованного"))
                .person();
    }
}
