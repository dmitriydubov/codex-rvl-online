package ru.projectx.contracts.entity;

public record StoredFile(String key, String hash, long size, String contentType) {
}
