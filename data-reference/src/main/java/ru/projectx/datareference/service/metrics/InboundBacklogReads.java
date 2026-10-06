package ru.projectx.datareference.service.metrics;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.QuarantineRepository;
import ru.projectx.datareference.repository.StatusBacklog;

/**
 * Читает из базы снимок очереди разбора ({@link InboundBacklog}): четыре запроса, каждый считает, а не выбирает
 * строки, и тела сообщений не читает. Пороги — те же, что у задач, которые с этим накоплением работают: срок
 * хранения тел и порог тревоги о зависших передачах из {@link ImportProperties}. Давность превращается в момент по
 * часам сервиса один раз на снимок: «старше порога» значит принято раньше, чем «момент снимка минус порог».
 *
 * <p>Все четыре запроса идут в одной транзакции только для чтения: снимок согласован, и база знает, что писать в
 * этой транзакции не будут.
 */
@Component
public class InboundBacklogReads {

    private final InboundMessageRepository inboundMessages;
    private final QuarantineRepository quarantine;
    private final ImportProperties properties;
    private final Clock clock;

    public InboundBacklogReads(InboundMessageRepository inboundMessages, QuarantineRepository quarantine,
            ImportProperties properties, Clock clock) {

        this.inboundMessages = inboundMessages;
        this.quarantine = quarantine;
        this.properties = properties;
        this.clock = clock;
    }

    /** Снимок на текущий момент. */
    @Transactional(readOnly = true)
    public InboundBacklog read() {
        Instant now = clock.instant();
        return new InboundBacklog(
                inboundMessages.countUnfinishedByStreamAndStatus().stream().map(InboundBacklogReads::statusCount).toList(),
                inboundMessages.countTransfersBufferedBefore(now.minus(properties.stuckTransferThreshold())),
                new InboundBacklog.Overdue(
                        inboundMessages.countPayloadsToForgetReceivedBefore(now.minus(properties.payloadTtl())),
                        quarantine.countBodiesReceivedBefore(now.minus(properties.quarantineTtl()))),
                now);
    }

    private static InboundBacklog.StatusCount statusCount(StatusBacklog row) {
        return new InboundBacklog.StatusCount(row.getStream(), row.getStatus(), row.getMessages(), row.getOldestReceivedAt());
    }
}
