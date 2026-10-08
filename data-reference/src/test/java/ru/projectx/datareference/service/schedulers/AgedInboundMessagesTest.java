package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import ru.projectx.datareference.repository.BufferedTransfer;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.QuarantineRepository;

/**
 * Порог давности превращается в момент по часам сервиса: «старше тридцати суток» значит принято раньше, чем «сейчас
 * минус тридцать суток». Сами запросы проверяет {@link AgedInboundMessagesQueryTest}, здесь только момент и предел.
 */
@ExtendWith(MockitoExtension.class)
class AgedInboundMessagesTest {

    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");

    @Mock
    private InboundMessageRepository repository;

    @Mock
    private QuarantineRepository quarantine;

    @Test
    void forgetPayloads_erasesRowsReceivedBeforeNowMinusAge() {
        when(repository.forgetPayloadsReceivedBefore(NOW.minus(Duration.ofDays(30)), 500)).thenReturn(17);

        assertThat(agedMessages().forgetPayloads(Duration.ofDays(30), 500)).isEqualTo(17);
    }

    @Test
    void forgetQuarantineBodies_erasesRowsReceivedBeforeNowMinusAge() {
        when(quarantine.forgetBodies(NOW.minus(Duration.ofDays(7)), 50)).thenReturn(3);

        assertThat(agedMessages().forgetQuarantineBodies(Duration.ofDays(7), 50)).isEqualTo(3);
    }

    @Test
    void transfersBufferedLongerThan_asksForTransfersStartedBeforeNowMinusAge() {
        List<BufferedTransfer> stuck = List.of(mock(BufferedTransfer.class));
        when(repository.findTransfersBufferedBefore(NOW.minus(Duration.ofHours(1)), Limit.of(20))).thenReturn(stuck);

        assertThat(agedMessages().transfersBufferedLongerThan(Duration.ofHours(1), 20)).isSameAs(stuck);
    }

    /** Закрытию нужны все зависшие передачи, без предела числа названных в предупреждении. */
    @Test
    void allTransfersBufferedLongerThan_asksWithoutLimit() {
        List<BufferedTransfer> stuck = List.of(mock(BufferedTransfer.class));
        when(repository.findTransfersBufferedBefore(NOW.minus(Duration.ofHours(1)), Limit.unlimited()))
                .thenReturn(stuck);

        assertThat(agedMessages().allTransfersBufferedLongerThan(Duration.ofHours(1))).isSameAs(stuck);
    }

    private AgedInboundMessages agedMessages() {
        return new AgedInboundMessages(repository, quarantine, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
