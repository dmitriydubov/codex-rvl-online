package ru.projectx.datareference.repository.inbound;

import java.time.Instant;
import java.util.UUID;

public interface BufferedTransfer {

    UUID getCorrelationId();

    Instant getFirstPartReceivedAt();

    long getParts();
}
