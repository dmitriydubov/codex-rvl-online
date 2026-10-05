package ru.projectx.datareference.acceptance;

import org.jspecify.annotations.Nullable;

/**
 * Строка сырья {@code inbound_message} так, как её видит дежурный: статус, число отложенных попыток, причина
 * последнего исхода и тело сообщения. Тело и причина пустые, если их стёрли по сроку хранения.
 */
record InboundRow(String status, int attempt, @Nullable String lastError, @Nullable String payload) {
}
