package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Тогда;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.contracts.message.status.StatusEvents;

public class OptionalIssuanceSteps {

    private final IssuanceRecords records;
    private final OutboxRecords outbox;
    private Optional<PolicySnapshot> remembered = Optional.empty();

    public OptionalIssuanceSteps(IssuanceRecords records, OutboxRecords outbox) {
        this.records = records;
        this.outbox = outbox;
    }

    @Тогда("в сохранённом снимке обеих сторон договора КМ участника {string} пусты отчество, адрес, серия, кем и когда выдан документ и код подразделения")
    public void contractPartiesHaveNoOptionalFields(String participantId) {
        assertThat(records.optionalPartyFields(participantId)).containsExactlyInAnyOrder(
                new IssuanceRecords.PartyOptionalFields("POLICYHOLDER", true),
                new IssuanceRecords.PartyOptionalFields("INSURED", true));
    }

    @Дано("запись полиса ЛМ участника {string} заявки {string} запомнена для сравнения")
    public void rememberPolicy(String participantId, String requestId) {
        List<String> rows = records.lmPolicyRows(participantId, requestId);
        assertThat(rows).hasSize(1);
        remembered = Optional.of(new PolicySnapshot(participantId, requestId, rows, statusMessageIds(requestId)));
    }

    @Тогда("запись полиса ЛМ участника {string} заявки {string} совпадает с запомненной, включая реквизиты и версию")
    public void policyIsUnchanged(String participantId, String requestId) {
        PolicySnapshot before = snapshot();
        assertThat(participantId).isEqualTo(before.participantId());
        assertThat(requestId).isEqualTo(before.requestId());
        assertThat(records.lmPolicyRows(participantId, requestId)).isEqualTo(before.rows());
        assertThat(statusMessageIds(requestId)).as("записи о состоянии выпуска в журнале исходящих")
                .containsExactlyInAnyOrderElementsOf(before.statusMessageIds());
    }

    @Тогда("выпущенный полис ЛМ остался с прежними реквизитами и версией")
    public void previouslyIssuedPolicyIsUnchanged() {
        PolicySnapshot before = snapshot();
        policyIsUnchanged(before.participantId(), before.requestId());
    }

    private PolicySnapshot snapshot() {
        return remembered.orElseThrow(() -> new AssertionError("прежняя запись полиса не запомнена"));
    }

    private List<UUID> statusMessageIds(String requestId) {
        return outbox.ofRequest(requestId).stream()
                .filter(entry -> StatusEvents.CONTRACT_STATUS_CHANGED.equals(entry.eventType()))
                .map(OutboxRecords.Entry::messageId).toList();
    }

    private record PolicySnapshot(String participantId, String requestId, List<String> rows, List<UUID> statusMessageIds) {
    }
}
