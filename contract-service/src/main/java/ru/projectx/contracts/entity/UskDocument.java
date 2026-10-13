package ru.projectx.contracts.entity;

import org.jspecify.annotations.Nullable;

public record UskDocument(PrintedFormType type, String fileUrl, @Nullable Long fileSize) {
}
