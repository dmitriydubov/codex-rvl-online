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
 * Порог давности превращается в момент по часам сервиса: «старше тридцати суток» значит принято раньше, чем
 * «сейчас минус тридцать суток». Сами запросы к базе проверяет {@link AgedInboundMessagesQueryTest}; здесь —
 * с каким моментом и каким пределом их зовут.
 */
@ExtendWith(MockitoExtension.class)
class AgedInboundMessagesTest {

    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");

    @Mock
    private InboundMessageRepository repository;

    @Mock
    private QuarantineRepository quarantine;

    @Test
    void forgetPolicyPayloads_erasesRowsReceivedBeforeNowMinusAge() {
        when(repository.forgetPolicyPayloads(NOW.minus(Duration.ofDays(30)), 500)).thenReturn(17);

        assertThat(agedMessages().forgetPolicyPayloads(Duration.ofDays(30), 500)).isEqualTo(17);
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

    @Test
    void countTransfersBufferedLongerThan_countsTransfersStartedBeforeNowMinusAge() {
        when(repository.countTransfersBufferedBefore(NOW.minus(Duration.ofHours(1)))).thenReturn(4L);

        assertThat(agedMessages().countTransfersBufferedLongerThan(Duration.ofHours(1))).isEqualTo(4L);
    }

    @Test
    void countPolicyPayloadsOlderThan_countsRowsReceivedBeforeNowMinusAge() {
        when(repository.countPolicyPayloadsReceivedBefore(NOW.minus(Duration.ofDays(30)))).thenReturn(9L);

        assertThat(agedMessages().countPolicyPayloadsOlderThan(Duration.ofDays(30))).isEqualTo(9L);
    }

    @Test
    void countQuarantineBodiesOlderThan_countsRowsReceivedBeforeNowMinusAge() {
        when(quarantine.countBodiesReceivedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(2L);

        assertThat(agedMessages().countQuarantineBodiesOlderThan(Duration.ofDays(7))).isEqualTo(2L);
    }

    private AgedInboundMessages agedMessages() {
        return new AgedInboundMessages(repository, quarantine, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
