package ru.projectx.contracts.service.storage;

import java.io.InputStream;

public record AttachmentContent(InputStream stream, String contentType, long size, String hash) {
}
