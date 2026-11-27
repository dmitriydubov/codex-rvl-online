package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class AttachmentContractStates {

    private final JdbcClient jdbc;
    private final IssuanceRecords records;

    AttachmentContractStates(JdbcClient jdbc, IssuanceRecords records) {
        this.jdbc = jdbc;
        this.records = records;
    }

    UUID id(String request, String participant) {
        return records.latestContractId(participant, request);
    }

    void expect(String request, String participant, String status) {
        UUID id = id(request, participant);
        assertThat(jdbc.sql("select status::text from contracts where id = :id")
                .param("id", id).query(String.class).single()).isEqualTo(status);
    }

    void force(UUID contract, String status) {
        assertThat(jdbc.sql("update contracts set status = cast(:status as contract_status) where id = :id")
                .param("status", status).param("id", contract).update()).isEqualTo(1);
    }

    void recordSubmission(UUID contract, String status) {
        assertThat(jdbc.sql("""
                insert into outbox_messages (id, aggregate_id, aggregate_type, message_id, correlation_id, topic,
                                            event_type, entity_version, payload, status, attempts, created_at)
                select gen_random_uuid(), id, 'CONTRACT', gen_random_uuid(), individual_request_id,
                       'rvl.km-contract.submitted', 'KmContractSubmitted', entity_version,
                       '{}'::jsonb, cast(:status as outbox_status), 0, updated_at
                  from contracts where id = :contract
                """).param("status", status).param("contract", contract).update()).isEqualTo(1);
    }

    void clearSubmissionBody(UUID contract) {
        assertThat(jdbc.sql("update outbox_messages set payload = null where aggregate_id = :contract"
                + " and event_type = 'KmContractSubmitted'").param("contract", contract).update()).isEqualTo(1);
    }

    long submissions(UUID contract) {
        return jdbc.sql("select count(*) from outbox_messages where aggregate_id = :contract"
                + " and event_type = 'KmContractSubmitted'").param("contract", contract).query(Long.class).single();
    }

    void expectNoSubmittedDocuments(UUID contract) {
        assertThat(jdbc.sql("""
                select payload #> '{data,documents}' = '[]'::jsonb
                  from outbox_messages
                 where event_type = 'KmContractSubmitted' and aggregate_id = :contract
                """).param("contract", contract).query(Boolean.class).single()).isTrue();
    }

    UUID copyAs(UUID template, String participant, String status) {
        return jdbc.sql("""
                insert into contracts (id, number, status, individual_request_id, insured_participant_id,
                                       policyholder_participant_id, insurance_contract_id, insurer_policy_id,
                                       program_id, date_from, date_to, premium_amount, entity_version, created_at, updated_at)
                select gen_random_uuid(), number || '-history', cast(:status as contract_status), individual_request_id,
                       :participant, policyholder_participant_id, insurance_contract_id, insurer_policy_id,
                       program_id, date_from, date_to, premium_amount, entity_version, created_at, updated_at
                  from contracts where id = :template
                returning id
                """).param("template", template).param("participant", UUID.fromString(participant))
                .param("status", status).query(UUID.class).single();
    }
}
