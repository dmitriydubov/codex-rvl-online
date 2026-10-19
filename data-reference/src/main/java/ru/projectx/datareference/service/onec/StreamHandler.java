package ru.projectx.datareference.service.onec;

import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.OneCStream;
import tools.jackson.databind.node.ObjectNode;

public interface StreamHandler {

    OneCStream stream();

    ImportOutcome handle(InboundMessage message, ObjectNode data);
}
