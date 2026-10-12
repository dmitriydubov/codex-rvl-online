package ru.projectx.datareference.service.onec;

import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.node.ObjectNode;

public interface StreamHandler {

    OneCStream stream();

    ImportOutcome handle(InboundMessage message, ObjectNode data);
}
