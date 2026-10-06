package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.ParentKind;

/**
 * Защищает запись об отказе по сроку ожидания: она называет поток сообщения и родителя, которого оно не дождалось,
 * а сообщение, которое ничего не ждало, такой записи не даёт — это ошибка вызывающего, а не отказ по сроку.
 * Данные вымышленные.
 */
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
