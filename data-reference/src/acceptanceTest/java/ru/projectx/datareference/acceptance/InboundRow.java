package ru.projectx.datareference.acceptance;

import org.jspecify.annotations.Nullable;

/**
 * Строка сырья {@code inbound_message} так, как её видит дежурный: статус, причина последнего исхода
 * и тело сообщения. Тело пустое, если его стёрли по сроку хранения.
 */
record InboundRow(String status, @Nullable String lastError, @Nullable String payload) {
}
