package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.inbound.AwaitedParent;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundReceipt;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.entity.inbound.ParentKind;

class ExpiredParentWaitTest {

    private static final Instant RECEIVED = Instant.parse("2026-10-06T09:00:00Z");
    private static final AwaitedParent SANATORIUM =
            new AwaitedParent(ParentKind.CONTRAGENT, UUID.fromString("5a1c3e7d-9b24-4f60-8d13-c2e7f9a0b481"));

    @Test
    void waitingMessage_namesItsStreamAndParent() {
        InboundMessage contract = received(OneCStream.LPU_CONTRACTS);
        contract.markAwaitingParent(SANATORIUM, null, RECEIVED);

        assertThat(ExpiredParentWait.of(contract))
                .isEqualTo(new ExpiredParentWait(OneCStream.LPU_CONTRACTS, SANATORIUM, null));
    }

    @Test
    void messageThatWaitedForNothing_isRefused() {
        InboundMessage contract = received(OneCStream.LPU_CONTRACTS);

        assertThatThrownBy(() -> ExpiredParentWait.of(contract))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("сообщение не ждало родителя");
    }

    private static InboundMessage received(OneCStream stream) {
        return InboundMessage.receive(new InboundReceipt(UUID.randomUUID(), stream, "очередь", RECEIVED), "{}");
    }
}
