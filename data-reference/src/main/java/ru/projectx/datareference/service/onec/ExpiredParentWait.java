package ru.projectx.datareference.service.onec;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Сообщение 1С, отклонённое потому, что его родитель не пришёл за срок ожидания: что нужно журналу и метрике.
 *
 * @param transfer идентификатор передачи прайса, частью которой было сообщение; пусто у остальных потоков
 */
public record ExpiredParentWait(OneCStream stream, AwaitedParent parent, @Nullable UUID transfer) {

    /** @throws IllegalStateException если сообщение ничего не ждало */
    static ExpiredParentWait of(InboundMessage message) {
        AwaitedParent parent = message.outcome().parentWait()
                .orElseThrow(() -> new IllegalStateException("сообщение не ждало родителя"))
                .parent();
        return new ExpiredParentWait(message.receipt().stream(), parent, message.transferPart().correlationId());
    }
}
