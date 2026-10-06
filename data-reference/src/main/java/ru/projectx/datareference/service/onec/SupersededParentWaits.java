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
 * Закрывает сообщения, которые ждут родителя, когда применилось более новое сообщение той же записи справочника.
 *
 * <p>Версий у сообщений 1С нет, и в справочнике действует последнее полученное. Старое сообщение, применённое после
 * прихода родителя, затёрло бы более новые данные. Поэтому оно закрывается пропуском с причиной, которая называет
 * запись и более новое сообщение.
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
     * Пропускает сообщения потока применённого сообщения о записи {@code record}, принятые раньше него и ждущие
     * родителя. Работает в транзакции вызывающего: пропуск фиксируется вместе с применением.
     *
     * <p>Обычный цикл, а не стрим: каждый шаг меняет сообщение.
     */
    @Transactional
    void closeOlderThan(InboundReceipt applied, UUID record) {
        String reason = "по записи %s уже применено более новое сообщение %s".formatted(record, applied.messageId());
        for (InboundMessage older : waits.findAwaitingOfRecordBefore(applied.stream(), record, applied.receivedAt())) {
            older.markSkipped(reason, clock.instant());
            transitions.record(older);
        }
    }
}
