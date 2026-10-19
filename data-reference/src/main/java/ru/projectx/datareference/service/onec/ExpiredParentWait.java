package ru.projectx.datareference.service.onec;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.inbound.AwaitedParent;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.OneCStream;

public record ExpiredParentWait(OneCStream stream, AwaitedParent parent, @Nullable UUID transfer) {

    static ExpiredParentWait of(InboundMessage message) {
        AwaitedParent parent = message.outcome().parentWait()
                .orElseThrow(() -> new IllegalStateException("сообщение не ждало родителя"))
                .parent();
        return new ExpiredParentWait(message.receipt().stream(), parent, message.transferPart().correlationId());
    }
}
