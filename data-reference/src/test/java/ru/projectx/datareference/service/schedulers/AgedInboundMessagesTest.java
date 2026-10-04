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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.BufferedTransfer;
import ru.projectx.datareference.repository.InboundMessageRepository;

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

    /**
     * Поток — параметр запроса, а не поток его сегодняшнего вызывающего: проверяется каждый, иначе зашитые
     * в запрос полисы прошли бы этот тест незамеченными.
     */
    @ParameterizedTest
    @EnumSource(OneCStream.class)
    void forgetPayloads_erasesRowsReceivedBeforeNowMinusAge(OneCStream stream) {
        when(repository.forgetPayloads(stream, NOW.minus(Duration.ofDays(30)), 500)).thenReturn(17);

        assertThat(agedMessages().forgetPayloads(stream, Duration.ofDays(30), 500)).isEqualTo(17);
    }

    @Test
    void transfersBufferedLongerThan_asksForTransfersStartedBeforeNowMinusAge() {
        List<BufferedTransfer> stuck = List.of(mock(BufferedTransfer.class));
        when(repository.findTransfersBufferedBefore(NOW.minus(Duration.ofHours(1)), Limit.of(20))).thenReturn(stuck);

        assertThat(agedMessages().transfersBufferedLongerThan(Duration.ofHours(1), 20)).isSameAs(stuck);
    }

    private AgedInboundMessages agedMessages() {
        return new AgedInboundMessages(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
