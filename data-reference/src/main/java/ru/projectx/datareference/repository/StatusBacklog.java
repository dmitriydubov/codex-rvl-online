package ru.projectx.datareference.repository;

import java.time.Instant;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;

public interface StatusBacklog {

    OneCStream getStream();

    InboundStatus getStatus();

    long getMessages();

    Instant getOldestReceivedAt();
}
