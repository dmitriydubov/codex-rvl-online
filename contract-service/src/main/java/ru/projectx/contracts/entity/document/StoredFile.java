package ru.projectx.contracts.entity.document;

public record StoredFile(String key, String hash, long size, String contentType) {
}
