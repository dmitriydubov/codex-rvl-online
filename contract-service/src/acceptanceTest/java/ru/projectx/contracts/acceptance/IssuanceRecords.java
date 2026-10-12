package ru.projectx.contracts.acceptance;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class IssuanceRecords {

    private static final String RECORDS = """
            select 'договор КМ' as document, id, status::text as status, number, date_from, date_to, premium_amount,
                   policyholder_participant_id, insurance_contract_id, insurer_policy_id, entity_version,
                   cancel_reason::text as cancel_reason, cancel_comment, cancelled_at, error_code, error_message, created_at
              from contracts where individual_request_id = :request and insured_participant_id = :participant
            union all
            select 'полис ЛМ', id, status::text, null, date_from, date_to, null,
                   policyholder_participant_id, insurance_contract_id, insurer_policy_id, entity_version,
                   cancel_reason::text, cancel_comment, cancelled_at, error_code, error_message, created_at
              from lm_policies where individual_request_id = :request and participant_id = :participant
            order by created_at, id
            """;

    private static final String PERSONS = """
            select role::text as role, participant_id, last_name, first_name, middle_name, birth_date, gender::text as gender,
                   relation_type, document_type::text as document_type, document_series, document_number
              from contract_persons
             where contract_id = :owner or lm_policy_id = :owner
             order by contract_persons.role
            """;

    private final JdbcClient jdbc;

    public IssuanceRecords(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Record(String document, UUID id, String status, @Nullable String number, LocalDate dateFrom, LocalDate dateTo,
            @Nullable BigDecimal premium, UUID policyholder, UUID insuranceContract, UUID insurerPolicy, int version,
            @Nullable String cancelReason, @Nullable String cancelComment, @Nullable Instant cancelledAt,
            @Nullable String errorCode, @Nullable String errorMessage) {

        String summary() {
            return document + " " + status + Optional.ofNullable(cancelReason).map(reason -> " " + reason).orElse("");
        }
    }

    record Person(String role, UUID participant, String lastName, String firstName, @Nullable String middleName,
            LocalDate birthDate, String gender, @Nullable String relationType, String documentType,
            @Nullable String documentSeries, String documentNumber) {
    }

    List<Record> of(String participantId, String requestId) {
        return jdbc.sql(RECORDS).param("request", UUID.fromString(requestId))
                .param("participant", UUID.fromString(participantId)).query(this::record).list();
    }

    List<Person> personsOf(UUID owner) {
        return jdbc.sql(PERSONS).param("owner", owner).query(this::person).list();
    }

    private Record record(ResultSet row, int index) throws SQLException {
        OffsetDateTime cancelledAt = row.getObject("cancelled_at", OffsetDateTime.class);
        return new Record(row.getString("document"), row.getObject("id", UUID.class), row.getString("status"),
                row.getString("number"), row.getObject("date_from", LocalDate.class), row.getObject("date_to", LocalDate.class),
                row.getBigDecimal("premium_amount"), row.getObject("policyholder_participant_id", UUID.class),
                row.getObject("insurance_contract_id", UUID.class), row.getObject("insurer_policy_id", UUID.class),
                row.getInt("entity_version"), row.getString("cancel_reason"), row.getString("cancel_comment"),
                cancelledAt == null ? null : cancelledAt.toInstant(), row.getString("error_code"),
                row.getString("error_message"));
    }

    private Person person(ResultSet row, int index) throws SQLException {
        return new Person(row.getString("role"), row.getObject("participant_id", UUID.class), row.getString("last_name"),
                row.getString("first_name"), row.getString("middle_name"), row.getObject("birth_date", LocalDate.class),
                row.getString("gender"), row.getString("relation_type"), row.getString("document_type"),
                row.getString("document_series"), row.getString("document_number"));
    }
}
