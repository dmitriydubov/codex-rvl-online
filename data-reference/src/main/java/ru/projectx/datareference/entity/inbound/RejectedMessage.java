package ru.projectx.datareference.entity.inbound;

import java.util.UUID;

public record RejectedMessage(UUID messageId, OneCStream stream, String sourceQueue, byte[] body) {
}
