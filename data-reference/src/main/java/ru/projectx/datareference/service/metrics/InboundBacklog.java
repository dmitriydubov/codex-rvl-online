package ru.projectx.datareference.service.metrics;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Снимок накопления в таблице сырых сообщений 1С на момент {@code takenAt}, из которого читают метрики, не обращаясь
 * к базе. В {@code statuses} нет пар «поток, статус» без сообщений.
 */
public record InboundBacklog(List<StatusCount> statuses, long stuckTransfers, Overdue overdue, Instant takenAt) {

    private static final Set<InboundStatus> PENDING = Set.of(InboundStatus.NEW, InboundStatus.RETRY);

    public InboundBacklog {
        statuses = List.copyOf(statuses);
    }

    /** До первого чтения из базы метрики показывают нули. */
    public static InboundBacklog empty(Instant takenAt) {
        return new InboundBacklog(List.of(), 0, new Overdue(0, 0, 0), takenAt);
    }

    public long messages(OneCStream stream, InboundStatus status) {
        return statuses.stream()
                .filter(count -> count.stream() == stream && count.status() == status)
                .mapToLong(StatusCount::messages)
                .sum();
    }

    public Optional<Instant> oldestPending(OneCStream stream) {
        return statuses.stream()
                .filter(count -> count.stream() == stream && PENDING.contains(count.status()))
                .map(StatusCount::oldestReceivedAt)
                .min(Comparator.naturalOrder());
    }

    public record StatusCount(OneCStream stream, InboundStatus status, long messages, Instant oldestReceivedAt) {
    }

    /** Персональные данные, пережившие срок хранения: не ноль значит, что стирание отстаёт или не работает. */
    public record Overdue(long payloads, long quarantineBodies, long policies) {
    }
}
