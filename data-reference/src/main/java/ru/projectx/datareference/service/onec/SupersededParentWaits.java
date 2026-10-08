package ru.projectx.datareference.service.onec;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.repository.ParentWaitRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

/**
 * Закрывает пропуском сообщения, которые ждут родителя, когда более новое сообщение той же записи применено или
 * пропущено. Версий у сообщений 1С нет, и старое сообщение, применённое позже, затёрло бы более новые данные или
 * вернуло бы запись, которую 1С уже назвала чужой.
 */
@Component
class SupersededParentWaits {

    private final ParentWaitRepository waits;
    private final InboundTransitionMetrics transitions;
    private final Clock clock;

    SupersededParentWaits(ParentWaitRepository waits, InboundTransitionMetrics transitions, Clock clock) {
        this.waits = waits;
        this.transitions = transitions;
        this.clock = clock;
    }

    /**
     * Работает в транзакции вызывающего, поэтому пропуск фиксируется вместе с исходом более нового сообщения.
     *
     * @param newerOutcome что стало с более новым сообщением, как это звучит в причине: «применено» или «пропущено»
     */
    @Transactional
    void closeOlderThan(InboundReceipt newer, UUID record, String newerOutcome) {
        String reason = "по записи %s уже %s более новое сообщение %s"
                .formatted(record, newerOutcome, newer.messageId());
        for (InboundMessage older : waits.findAwaitingOfRecordBefore(newer.stream(), record, newer.receivedAt())) {
            older.markSkipped(reason, clock.instant());
            transitions.record(older);
        }
    }
}
