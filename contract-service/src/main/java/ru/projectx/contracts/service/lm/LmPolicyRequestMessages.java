package ru.projectx.contracts.service.lm;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.issuance.IssuanceState;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.PersonSnapshot;
import ru.projectx.contracts.message.InsuredPerson;
import ru.projectx.contracts.message.LmPolicyEvents;
import ru.projectx.contracts.message.LmPolicyRequest;
import ru.projectx.contracts.message.LmPolicyRequested;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.message.StatusEvents;
import ru.projectx.contracts.service.usk.ParticipantDocuments;
import ru.projectx.contracts.service.usk.RequestTerms;
import ru.projectx.contracts.service.usk.UskRequestMessages;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LmPolicyRequestMessages implements UskRequestMessages<LmPolicy, LmRequestFacts> {

    private final JsonMapper json;
    private final Clock clock;

    public LmPolicyRequestMessages(JsonMapper json, Clock clock) {
        this.json = json;
        this.clock = clock;
    }

    @Override
    public MessageMeta meta(LmPolicy policy, Instant now) {
        return new MessageMeta(UUID.randomUUID(), policy.state().request().individualRequestId(),
                LmPolicyEvents.LM_POLICY_REQUEST_TOPIC, LmPolicyEvents.LM_POLICY_REQUESTED, StatusEvents.SCHEMA_VERSION,
                OffsetDateTime.ofInstant(now, clock.getZone()), policy.state().entityVersion(), StatusEvents.SOURCE);
    }

    @Override
    public String write(MessageMeta meta, LmPolicy policy, LmRequestFacts facts) {
        IssuanceState state = policy.state();
        LmPolicyRequest data = new LmPolicyRequest(policy.id(), RequestTerms.of(state, facts.identifiers()),
                state.policyholderParticipantId(), insured(facts.insured()));
        return json.writeValueAsString(new LmPolicyRequested(meta, data));
    }

    private static InsuredPerson insured(PersonSnapshot person) {
        return new InsuredPerson(person.participantId(), person.relationType(), person.lastName(), person.firstName(),
                person.middleName(), person.birthDate(), person.gender().name(),
                ParticipantDocuments.of(person.document()), person.address());
    }
}
