package ru.projectx.datareference.repository.inbound;

import java.time.Instant;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.entity.inbound.OneCStream;

public interface StatusBacklog {

    OneCStream getStream();

    InboundStatus getStatus();

    long getMessages();

    Instant getOldestReceivedAt();
}
