package ru.projectx.datareference.acceptance;

import org.jspecify.annotations.Nullable;

record InboundRow(String status, int attempt, @Nullable String lastError, @Nullable String payload) {
}
