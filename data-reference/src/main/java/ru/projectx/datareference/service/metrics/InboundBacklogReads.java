package ru.projectx.datareference.service.metrics;

import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.StatusBacklog;
import ru.projectx.datareference.service.schedulers.AgedInboundMessages;

/**
 * Читает из базы снимок очереди разбора ({@link InboundBacklog}): четыре запроса, каждый считает, а не выбирает
 * строки, и тела сообщений не читает. Пороги — те же, что у задач, которые с этим накоплением работают: срок
 * хранения тел и порог тревоги о зависших передачах из {@link ImportProperties}.
 *
 * <p>Все четыре запроса идут в одной транзакции только для чтения: снимок согласован, и база знает, что писать в
 * этой транзакции не будут.
 */
@Component
public class InboundBacklogReads {

    private final InboundMessageRepository repository;
    private final AgedInboundMessages agedMessages;
    private final ImportProperties properties;
    private final Clock clock;

    public InboundBacklogReads(InboundMessageRepository repository, AgedInboundMessages agedMessages,
            ImportProperties properties, Clock clock) {

        this.repository = repository;
        this.agedMessages = agedMessages;
        this.properties = properties;
        this.clock = clock;
    }

    /** Снимок на текущий момент. */
    @Transactional(readOnly = true)
    public InboundBacklog read() {
        return new InboundBacklog(
                repository.countUnfinishedByStreamAndStatus().stream().map(InboundBacklogReads::statusCount).toList(),
                agedMessages.countTransfersBufferedLongerThan(properties.stuckTransferThreshold()),
                new InboundBacklog.Overdue(
                        agedMessages.countPolicyPayloadsOlderThan(properties.payloadTtl()),
                        agedMessages.countQuarantineBodiesOlderThan(properties.quarantineTtl())),
                clock.instant());
    }

    private static InboundBacklog.StatusCount statusCount(StatusBacklog row) {
        return new InboundBacklog.StatusCount(row.getStream(), row.getStatus(), row.getMessages(), row.getOldestReceivedAt());
    }
}
