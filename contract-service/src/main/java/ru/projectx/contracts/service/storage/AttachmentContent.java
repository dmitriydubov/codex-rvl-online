package ru.projectx.contracts.service.storage;

import org.jspecify.annotations.Nullable;

public record AttachmentContent(@Nullable String contentType, long size, ContentSource source) {
}
