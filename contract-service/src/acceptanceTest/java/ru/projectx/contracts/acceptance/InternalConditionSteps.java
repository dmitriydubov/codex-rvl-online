package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.jdbc.core.simple.JdbcClient;

public class InternalConditionSteps {

    private static final String DISALLOWED_CONTENT_TYPE = "image/gif";
    private static final String ACCEPTANCES = "contract_acceptances";
    private static final String PERSONS = "contract_persons";
    private static final String ACCEPTANCE_OF = "contract_id = :owner";
    private static final String PERSON_OF = "(contract_id = :owner or lm_policy_id = :owner) and role::text = '%s'";

    private final IssuanceRecords records;
    private final JdbcClient jdbc;
    private final Map<String, BiFunction<UUID, Participant, Runnable>> conditions = Map.of(
            "нет записи акцепта", (owner, participant) -> stashRows(ACCEPTANCES, ACCEPTANCE_OF, owner),
            "нет снимка страхователя", (owner, participant) ->
                    stashRows(PERSONS, PERSON_OF.formatted("POLICYHOLDER"), owner),
            "нет снимка застрахованного", (owner, participant) ->
                    stashRows(PERSONS, PERSON_OF.formatted("INSURED"), owner),
            "нет момента оплаты", (owner, participant) -> erasePaidAt(owner),
            "тип справки не из допустимых", (owner, participant) -> replaceContentTypes(participant));
    private final Map<String, Runnable> restorations = new HashMap<>();

    public InternalConditionSteps(IssuanceRecords records, JdbcClient jdbc) {
        this.records = records;
        this.jdbc = jdbc;
    }

    @Дано("стенд нарушает внутреннее условие {string} у записи о выпуске участника {string} заявки {string}")
    public void conditionBroken(String condition, String participantId, String requestId) {
        assertThat(conditions).as("внутренние условия, которые умеет нарушать стенд").containsKey(condition);
        Participant participant = new Participant(UUID.fromString(participantId), UUID.fromString(requestId));
        UUID owner = records.of(participantId, requestId).getLast().id();
        restorations.put(key(condition, participantId, requestId), conditions.get(condition).apply(owner, participant));
    }

    @Когда("стенд восстанавливает внутреннее условие {string} у записи о выпуске участника {string} заявки {string}")
    public void conditionRestored(String condition, String participantId, String requestId) {
        String key = key(condition, participantId, requestId);
        assertThat(restorations).as("условия, нарушенные стендом в этом сценарии").containsKey(key);
        restorations.remove(key).run();
    }

    private Runnable stashRows(String table, String rowsOfOwner, UUID owner) {
        String stash = "stand_broken_" + table;
        jdbc.sql("create table if not exists " + stash + " (like " + table + ")").update();
        int moved = jdbc.sql("insert into " + stash + " select * from " + table + " where " + rowsOfOwner)
                .param("owner", owner).update();
        assertThat(moved).as("строки %s записи %s, которые удаляет стенд", table, owner).isPositive();
        jdbc.sql("delete from " + table + " where " + rowsOfOwner).param("owner", owner).update();
        return () -> {
            jdbc.sql("insert into " + table + " select * from " + stash + " where " + rowsOfOwner)
                    .param("owner", owner).update();
            jdbc.sql("delete from " + stash + " where " + rowsOfOwner).param("owner", owner).update();
        };
    }

    private Runnable erasePaidAt(UUID contract) {
        OffsetDateTime paidAt = jdbc.sql("select paid_at from contracts where id = :id").param("id", contract)
                .query(OffsetDateTime.class).single();
        jdbc.sql("update contracts set paid_at = null where id = :id").param("id", contract).update();
        return () -> jdbc.sql("update contracts set paid_at = :paidAt where id = :id").param("paidAt", paidAt)
                .param("id", contract).update();
    }

    private Runnable replaceContentTypes(Participant participant) {
        List<Map<String, Object>> certificates = jdbc.sql("""
                        select id, content_type from contract_documents
                         where individual_request_id = :request and participant_id = :participant
                        """).param("request", participant.request()).param("participant", participant.id())
                .query().listOfRows();
        assertThat(certificates).as("справки участника %s", participant.id()).isNotEmpty();
        certificates.forEach(certificate -> setContentType(certificate.get("id"), DISALLOWED_CONTENT_TYPE));
        return () -> certificates.forEach(certificate -> setContentType(certificate.get("id"),
                certificate.get("content_type")));
    }

    private void setContentType(Object certificate, Object contentType) {
        jdbc.sql("update contract_documents set content_type = :type where id = :id").param("type", contentType)
                .param("id", certificate).update();
    }

    private static String key(String condition, String participantId, String requestId) {
        return condition + " " + participantId + " " + requestId;
    }

    private record Participant(UUID id, UUID request) {
    }
}
