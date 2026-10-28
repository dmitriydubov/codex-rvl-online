package ru.projectx.contracts.service.inbox;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.task.ContractTaskCancellation;
import ru.projectx.contracts.service.inbox.parse.JsonFields;
import ru.projectx.contracts.service.inbox.parse.MessageBodies;
import ru.projectx.contracts.service.inbox.parse.PayloadParser;

@Component
public class TaskCancellationParser implements PayloadParser<ContractTaskCancellation> {

    private static final String CANCELLED_AT = "cancelled_at";

    private final MessageBodies bodies;

    public TaskCancellationParser(MessageBodies bodies) {
        this.bodies = bodies;
    }

    @Override
    public ContractTaskCancellation parse(String payload) {
        JsonFields data = bodies.data(payload);
        try {
            return new ContractTaskCancellation(data.uuid("individual_request_id"),
                    OffsetDateTime.parse(data.text(CANCELLED_AT)).toInstant(), data.text("cancel_reason"),
                    data.textOrNull("cancel_comment"));
        }
        catch (DateTimeParseException notMoment) {
            throw ContractViolationException.invalidValue(data.pathOf(CANCELLED_AT), notMoment);
        }
    }
}
