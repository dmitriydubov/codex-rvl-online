package ru.projectx.datareference.repository;

import java.time.Instant;
import java.util.UUID;

public interface BufferedTransfer {

    UUID getCorrelationId();

    Instant getFirstPartReceivedAt();

    long getParts();
}
