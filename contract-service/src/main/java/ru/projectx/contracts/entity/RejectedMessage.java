package ru.projectx.contracts.entity;

import java.util.UUID;

public record RejectedMessage(UUID messageId, String sourceQueue, byte[] body) {
}
