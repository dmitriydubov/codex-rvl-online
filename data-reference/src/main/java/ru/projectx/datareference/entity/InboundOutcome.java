package ru.projectx.datareference.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Чем на сейчас закончилась обработка сообщения 1С: статус, число сорвавшихся попыток, время следующей попытки,
 * текст последнего отказа и момент завершения.
 *
 * <p>Это снимок для чтения. Меняется исход только переходами самого сообщения, каждый из которых записывает его
 * целиком.
 *
 * @param status      статус обработки
 * @param attempt     сколько раз обработка уже сорвалась временной ошибкой
 * @param nextRetryAt когда назначена следующая попытка; пусто, если повтор не ожидается
 * @param lastError   текст последнего отказа, а у пропущенного сообщения — причина пропуска
 * @param processedAt когда сообщение разложено по таблицам или пропущено; пусто, пока обработка не закончена
 */
public record InboundOutcome(
        InboundStatus status,
        int attempt,
        @Nullable Instant nextRetryAt,
        @Nullable String lastError,
        @Nullable Instant processedAt) {
}
