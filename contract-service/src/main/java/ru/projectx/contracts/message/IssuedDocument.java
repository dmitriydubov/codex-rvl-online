package ru.projectx.contracts.message;

import org.jspecify.annotations.Nullable;

public record IssuedDocument(String docType, String fileUrl, @Nullable Long fileSize) {
}
