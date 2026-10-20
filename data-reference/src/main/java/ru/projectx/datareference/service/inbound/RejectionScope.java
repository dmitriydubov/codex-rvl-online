package ru.projectx.datareference.service.inbound;

import java.util.List;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.OneCStream;

public interface RejectionScope {

    OneCStream stream();

    List<InboundMessage> reject(InboundMessage message, String reason);
}
