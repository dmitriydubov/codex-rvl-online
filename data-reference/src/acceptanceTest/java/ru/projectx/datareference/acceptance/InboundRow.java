package ru.projectx.datareference.acceptance;

import org.jspecify.annotations.Nullable;

/**
 * Строка сырья {@code inbound_message}, как её видит дежурный: статус, причина последнего
 * исхода и тело сообщения (пусто, если стёрто по сроку хранения).
 */
record InboundRow(String status, @Nullable String lastError, @Nullable String payload) {
}
