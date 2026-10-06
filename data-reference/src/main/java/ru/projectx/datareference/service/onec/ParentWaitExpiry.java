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
import ru.projectx.datareference.config.ParentWaitProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.ParentWaitRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

/**
 * Отклоняет сообщения 1С, родитель которых не пришёл за срок ожидания.
 *
 * <p>Срок считается от начала ожидания, а не от приёма: после долгого простоя разбора ребёнок и его родитель лежат в
 * сырье вместе, и отсчёт от приёма отклонил бы ребёнка раньше, чем разбор дошёл бы до родителя. Сообщение, родитель
 * которого уже есть, не отклоняется, даже если срок вышел: его применит ближайший разбор. Передача прайса
 * отклоняется целиком, все её части разом.
 */
@Component
public class ParentWaitExpiry {

    private final ParentWaitRepository waits;
    private final InboundMessageRepository inbound;
    private final InboundTransitionMetrics transitions;
    private final ParentWaitProperties waitProperties;
    private final ImportProperties importProperties;
    private final Clock clock;

    public ParentWaitExpiry(ParentWaitRepository waits, InboundMessageRepository inbound,
            InboundTransitionMetrics transitions, ParentWaitProperties waitProperties,
            ImportProperties importProperties, Clock clock) {

        this.waits = waits;
        this.inbound = inbound;
        this.transitions = transitions;
        this.waitProperties = waitProperties;
        this.importProperties = importProperties;
        this.clock = clock;
    }

    /**
     * Отклоняет не больше пачки разбора сообщений, срок ожидания которых истёк, вместе с остальными частями их
     * передач. Одна транзакция: отказ и счётчик переходов фиксируются вместе. Журнал пишет вызывающий, после
     * фиксации.
     *
     * <p>Обычный цикл, а не стрим: каждый шаг меняет сообщение.
     *
     * @return отклонённые сообщения в порядке приёма
     */
    @Transactional
    public List<ExpiredParentWait> expireOverdue() {
        Instant deadline = clock.instant().minus(waitProperties.parentWaitTimeout());
        List<InboundMessage> overdue = waits.findExpiredUnresolved(deadline, Limit.of(importProperties.batchSize()))
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
