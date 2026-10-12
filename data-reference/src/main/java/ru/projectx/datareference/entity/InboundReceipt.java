package ru.projectx.datareference.entity;

import java.time.Instant;
import java.util.UUID;

public record InboundReceipt(UUID messageId, OneCStream stream, String sourceQueue, Instant receivedAt) {
}
