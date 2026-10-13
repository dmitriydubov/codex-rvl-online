package ru.projectx.contracts.service.status;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.message.ContractStatusChanged;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.message.ParticipantStatus;
import ru.projectx.contracts.message.RequestIssuanceStatus;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StatusMessages {

    private final JsonMapper json;

    public StatusMessages(JsonMapper json) {
        this.json = json;
    }

    public String write(IssuanceSnapshot snapshot, MessageMeta meta) {
        List<ParticipantStatus> participants = snapshot.composition().stream()
                .map(participant -> ParticipantStatuses.of(participant, snapshot))
                .toList();
        RequestIssuanceStatus data = new RequestIssuanceStatus(snapshot.individualRequestId(),
                snapshot.requestStatus(), participants);
        return json.writeValueAsString(new ContractStatusChanged(meta, data));
    }
}
