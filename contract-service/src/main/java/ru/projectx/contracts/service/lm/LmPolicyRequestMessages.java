package ru.projectx.contracts.service.lm;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.IdentityDocument;
import ru.projectx.contracts.entity.IssuanceState;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.PersonSnapshot;
import ru.projectx.contracts.message.InsuredPerson;
import ru.projectx.contracts.message.LmPolicyEvents;
import ru.projectx.contracts.message.LmPolicyRequest;
import ru.projectx.contracts.message.LmPolicyRequested;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.message.ParticipantDocument;
import ru.projectx.contracts.message.StatusEvents;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LmPolicyRequestMessages {

    private final JsonMapper json;
    private final Clock clock;

    public LmPolicyRequestMessages(JsonMapper json, Clock clock) {
        this.json = json;
        this.clock = clock;
    }

    public MessageMeta meta(LmPolicy policy, Instant now) {
        return new MessageMeta(UUID.randomUUID(), policy.state().request().individualRequestId(),
                LmPolicyEvents.LM_POLICY_REQUEST_TOPIC, LmPolicyEvents.LM_POLICY_REQUESTED, StatusEvents.SCHEMA_VERSION,
                OffsetDateTime.ofInstant(now, clock.getZone()), policy.state().entityVersion(), StatusEvents.SOURCE);
    }

    public String write(MessageMeta meta, LmPolicy policy, PersonSnapshot insured, ExternalIdentifiers identifiers) {
        IssuanceState state = policy.state();
        LmPolicyRequest data = new LmPolicyRequest(policy.id(), state.request().individualRequestId(),
                identifiers.insuranceContractExternalId(), identifiers.insurerPolicyExternalId(),
                identifiers.programExternalId(), state.period().dateFrom(), state.period().dateTo(),
                state.policyholderParticipantId(), insured(insured));
        return json.writeValueAsString(new LmPolicyRequested(meta, data));
    }

    private static InsuredPerson insured(PersonSnapshot person) {
        IdentityDocument document = person.document();
        return new InsuredPerson(person.participantId(), person.relationType(), person.lastName(), person.firstName(),
                person.middleName(), person.birthDate(), person.gender().name(),
                new ParticipantDocument(document.type().name(), document.series(), document.number(),
                        document.issuedBy(), document.issuedAt(), document.divisionCode()),
                person.address());
    }
}
