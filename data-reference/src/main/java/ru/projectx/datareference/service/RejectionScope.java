package ru.projectx.datareference.service;

import java.util.List;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;

public interface RejectionScope {

    OneCStream stream();

    List<InboundMessage> reject(InboundMessage message, String reason);
}
