package ru.projectx.datareference.service.onec;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.ParentWaitRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

/**
 * Отклоняет сообщения 1С, родитель которых не пришёл за срок ожидания; передача прайса отклоняется целиком. Срок
 * считается от начала ожидания, а не от приёма, иначе после простоя разбора ребёнок отклонялся бы раньше, чем разбор
 * дойдёт до родителя. Сообщение, родитель которого уже есть, не отклоняется: его применит ближайший разбор.
 */
@Component
public class ParentWaitExpiry {

    private final ParentWaitRepository waits;
    private final InboundMessageRepository inbound;
    private final InboundTransitionMetrics transitions;
    private final ImportProperties properties;
    private final Clock clock;

    public ParentWaitExpiry(ParentWaitRepository waits, InboundMessageRepository inbound,
            InboundTransitionMetrics transitions, ImportProperties properties, Clock clock) {

        this.waits = waits;
        this.inbound = inbound;
        this.transitions = transitions;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Берёт не больше пачки разбора. Отказ и счётчик переходов фиксируются одной транзакцией, а журнал пишет
     * вызывающий после фиксации.
     *
     * @return отклонённые сообщения в порядке приёма
     */
    @Transactional
    public List<ExpiredParentWait> expireOverdue() {
        Instant deadline = clock.instant().minus(properties.parentWaitTimeout());
        List<InboundMessage> overdue = waits.findExpiredUnresolved(deadline, Limit.of(properties.batchSize()))
                .stream()
                .flatMap(this::withTransferParts)
                .distinct()
                .toList();
        for (InboundMessage message : overdue) {
            message.markParentWaitExpired();
            transitions.record(message);
        }
        return overdue.stream().map(ExpiredParentWait::of).toList();
    }

    /**
     * Сообщение и остальные ждущие части его передачи. Части читаются в той же транзакции, поэтому часть, уже
     * найденная выборкой, — тот же объект, и повторы снимает сравнение по ссылке.
     */
    private Stream<InboundMessage> withTransferParts(InboundMessage message) {
        return Optional.ofNullable(message.transferPart().correlationId())
                .map(inbound::findByCorrelationId)
                .orElseGet(() -> List.of(message))
                .stream()
                .filter(part -> part.outcome().status() == InboundStatus.AWAITING_PARENT);
    }
}
