package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.Gender;
import ru.projectx.contracts.entity.IdentityDocument;
import ru.projectx.contracts.entity.IdentityDocumentType;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.PersonSnapshot;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;

class LmPolicyRequestMessagesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:12:00Z");
    private static final String POLICY_ID = "8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39";
    private static final ExternalIdentifiers IDENTIFIERS = new ExternalIdentifiers(
            UUID.fromString("6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2"), UUID.fromString("8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88"),
            UUID.fromString("1d9a6e77-4c02-4b88-a15e-30f7b2c9de41"));
    private static final PersonSnapshot CHILD = new PersonSnapshot(Tasks.CHILD, "Тестов", "Тестик", "Тестович",
            LocalDate.parse("2017-05-30"), Gender.MALE, "CHILD", new IdentityDocument(IdentityDocumentType.BIRTH_CERTIFICATE,
                    "IV-M", "512345", "Тестовый орган", LocalDate.parse("2010-04-20"), "770-001"), "Тестовый адрес, 2");

    private final JsonMapper json = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final LmPolicyRequestMessages messages = new LmPolicyRequestMessages(json,
            Clock.fixed(NOW, ZoneId.of("Europe/Moscow")));
    private final LmPolicy policy = Issuances.policy(POLICY_ID, Tasks.CHILD);

    @Test
    void meta_namesTheRequestEventWithTheRequestAsCorrelationAndThePolicyVersion() {
        policy.submit(NOW);

        MessageMeta meta = messages.meta(policy, NOW);

        assertThat(meta.messageId()).isNotNull();
        assertThat(meta.correlationId()).isEqualTo(Tasks.REQUEST);
        assertThat(meta.topic()).isEqualTo("rvl.lm-policy.requested");
        assertThat(meta.eventType()).isEqualTo("LmPolicyRequested");
        assertThat(meta.schemaVersion()).isEqualTo("1.0");
        assertThat(meta.createdAt().toString()).isEqualTo("2026-02-10T10:12+03:00");
        assertThat(meta.entityVersion()).isEqualTo(2);
        assertThat(meta.source()).isEqualTo("RVL-CONTRACTS");
    }

    @Test
    void body_carriesTheEnvelopeExternalIdentifiersPolicyDatesAndTheInsuredSnapshotInSnakeCase() {
        MessageMeta meta = messages.meta(policy, NOW);

        JsonNode body = json.readTree(messages.write(meta, policy, new LmRequestFacts(CHILD, IDENTIFIERS)));

        JsonNode metaNode = body.path("meta");
        assertThat(metaNode.path("message_id").stringValue()).isEqualTo(meta.messageId().toString());
        assertThat(metaNode.path("created_at").stringValue()).isEqualTo("2026-02-10T10:12:00+03:00");
        assertThat(metaNode.path("entity_version").intValue()).isEqualTo(1);
        JsonNode data = body.path("data");
        assertThat(data.path("id").stringValue()).isEqualTo(POLICY_ID);
        assertThat(data.path("individual_request_id").stringValue()).isEqualTo(Tasks.REQUEST.toString());
        assertThat(data.path("insurance_contract_external_id").stringValue())
                .isEqualTo("8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88");
        assertThat(data.path("insurer_policy_external_id").stringValue()).isEqualTo("1d9a6e77-4c02-4b88-a15e-30f7b2c9de41");
        assertThat(data.path("program_external_id").stringValue()).isEqualTo("6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2");
        assertThat(data.path("date_from").stringValue()).isEqualTo("2026-04-10");
        assertThat(data.path("date_to").stringValue()).isEqualTo("2026-04-24");
        assertThat(data.path("policyholder_participant_id").stringValue()).isEqualTo(Tasks.INSURED.toString());
        JsonNode insured = data.path("insured");
        assertThat(insured.path("participant_id").stringValue()).isEqualTo(Tasks.CHILD.toString());
        assertThat(insured.path("relation_type").stringValue()).isEqualTo("CHILD");
        assertThat(insured.path("last_name").stringValue()).isEqualTo("Тестов");
        assertThat(insured.path("first_name").stringValue()).isEqualTo("Тестик");
        assertThat(insured.path("middle_name").stringValue()).isEqualTo("Тестович");
        assertThat(insured.path("birth_date").stringValue()).isEqualTo("2017-05-30");
        assertThat(insured.path("gender").stringValue()).isEqualTo("MALE");
        assertThat(insured.path("document").path("type").stringValue()).isEqualTo("BIRTH_CERTIFICATE");
        assertThat(insured.path("document").path("series").stringValue()).isEqualTo("IV-M");
        assertThat(insured.path("document").path("number").stringValue()).isEqualTo("512345");
        assertThat(insured.path("document").path("issued_by").stringValue()).isEqualTo("Тестовый орган");
        assertThat(insured.path("document").path("issued_at").stringValue()).isEqualTo("2010-04-20");
        assertThat(insured.path("document").path("division_code").stringValue()).isEqualTo("770-001");
        assertThat(insured.path("address").stringValue()).isEqualTo("Тестовый адрес, 2");
    }
}
