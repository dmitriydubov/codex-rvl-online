package ru.projectx.datareference.entity;

import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Снимок для чтения: чем на сейчас закончилась обработка сообщения 1С. {@code attempt} считает срывы временной
 * ошибкой, {@code lastError} у пропущенного сообщения хранит причину пропуска, а ожидаемый родитель
 * {@code awaited} остаётся и у отказа по истечении ожидания.
 */
public record InboundOutcome(
        InboundStatus status,
        int attempt,
        @Nullable Instant nextRetryAt,
        @Nullable String lastError,
        @Nullable Instant processedAt,
        @Nullable ParentWait awaited) {

    /** Чего и с какого момента ждёт сообщение; пусто, если оно родителя не ждёт. */
    public Optional<ParentWait> parentWait() {
        return Optional.ofNullable(awaited);
    }
}
