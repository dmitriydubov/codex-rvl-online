package ru.projectx.datareference.service.metrics;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Снимок того, что накопилось в таблице сырых сообщений 1С на момент {@code takenAt}: сколько сообщений каждого
 * потока ждут разбора, ждут частей передачи или ушли в отказ, сколько передач прайсов зависло и сколько тел с
 * персональными данными пережили свой срок хранения. Из снимка читают метрики; сам он к базе не ходит.
 *
 * @param statuses                пары «поток, статус» с числом сообщений; пар без сообщений здесь нет
 * @param stuckTransfers          передачи прайсов, которые ждут частей дольше порога тревоги
 * @param overdue                 персональные данные, не стёртые после срока хранения
 * @param takenAt                 момент снимка
 */
public record InboundBacklog(List<StatusCount> statuses, long stuckTransfers, Overdue overdue, Instant takenAt) {

    /** Статусы сообщений, которые ещё ждут разбора: новые и назначенные на повтор. */
    private static final Set<InboundStatus> PENDING = Set.of(InboundStatus.NEW, InboundStatus.RETRY);

    public InboundBacklog {
        statuses = List.copyOf(statuses);
    }

    /** Пустой снимок: до первого чтения из базы метрики показывают нули. */
    public static InboundBacklog empty(Instant takenAt) {
        return new InboundBacklog(List.of(), 0, new Overdue(0, 0, 0), takenAt);
    }

    /** Сколько сообщений потока {@code stream} в статусе {@code status}. */
    public long messages(OneCStream stream, InboundStatus status) {
        return statuses.stream()
                .filter(count -> count.stream() == stream && count.status() == status)
                .mapToLong(StatusCount::messages)
                .sum();
    }

    /** Когда принято самое давнее из сообщений потока, которые ждут разбора; пусто, если ждущих нет. */
    public Optional<Instant> oldestPending(OneCStream stream) {
        return statuses.stream()
                .filter(count -> count.stream() == stream && PENDING.contains(count.status()))
                .map(StatusCount::oldestReceivedAt)
                .min(Comparator.naturalOrder());
    }

    /**
     * Сообщения одного потока в одном статусе.
     *
     * @param messages         сколько их
     * @param oldestReceivedAt когда принято самое давнее
     */
    public record StatusCount(OneCStream stream, InboundStatus status, long messages, Instant oldestReceivedAt) {
    }

    /**
     * Персональные данные, которые пережили срок хранения и ещё не стёрты: стирание отстаёт или не работает.
     *
     * @param payloads          тела в таблице сырья, которые срок хранения должен был стереть
     * @param quarantineBodies  тела карантинных сообщений
     * @param policies          полисы, у которых срок хранения ФИО, даты рождения и почты истёк
     */
    public record Overdue(long payloads, long quarantineBodies, long policies) {
    }
}
