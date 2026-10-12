package ru.projectx.contracts.service.inbox;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.message.TaskParticipant;

@Component
public class ContractTaskParser {

    private final MessageBodies bodies;

    public ContractTaskParser(MessageBodies bodies) {
        this.bodies = bodies;
    }

    public ContractTask parse(String payload) {
        JsonFields data = bodies.data(payload);
        ContractTask task = new ContractTask(data.uuid("individual_request_id"), data.uuid("insurance_contract_id"),
                data.uuid("insurer_policy_id"), data.date("date_from"), data.date("date_to"),
                participants(data.requiredArray("participants")));
        requireSingleInsured(task.participants());
        return task;
    }

    private static List<TaskParticipant> participants(JsonFields array) {
        if (array.size() == 0) {
            throw new ContractViolationException("в " + array.path() + " нет ни одного участника");
        }
        List<TaskParticipant> participants = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            participants.add(ParticipantParser.parse(array.item(index), index));
        }
        return List.copyOf(participants);
    }

    private static void requireSingleInsured(List<TaskParticipant> participants) {
        long insured = participants.stream().filter(participant -> !participant.relative()).count();
        if (insured != 1) {
            throw new ContractViolationException(
                    "в составе поездки должно быть ровно одно застрахованное лицо, а не " + insured);
        }
    }
}
