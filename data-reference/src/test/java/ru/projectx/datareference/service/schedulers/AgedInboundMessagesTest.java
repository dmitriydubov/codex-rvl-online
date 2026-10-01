package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;

/**
 * Порог давности превращается в момент по часам сервиса: «старше тридцати суток» — значит
 * принято раньше, чем «сейчас минус тридцать суток». Запрос к базе здесь не проверяется —
 * он у репозитория, — проверяется, с каким моментом и какими условиями его зовут.
 */
@ExtendWith(MockitoExtension.class)
class AgedInboundMessagesTest {

    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final String QUEUE = "1c-usk_to_rvl-online_policies";

    @Mock
    private InboundMessageRepository repository;

    /**
     * Поток — параметр выборки, а не поток её сегодняшнего вызывающего: проверяется каждый,
     * иначе зашитые в выборку полисы прошли бы этот тест незамеченными.
     */
    @ParameterizedTest
    @EnumSource(OneCStream.class)
    void withPayload_selectsRowsReceivedBeforeNowMinusAge(OneCStream stream) {
        List<InboundMessage> expired = List.of(received(stream));
        when(repository.findByStreamAndPayloadIsNotNullAndReceivedAtLessThan(
                stream, NOW.minus(Duration.ofDays(30)), Limit.of(500))).thenReturn(expired);

        assertThat(agedMessages().withPayload(stream, Duration.ofDays(30), 500)).isSameAs(expired);
    }

    /** Статус — параметр так же, как поток: зашитый {@code BUFFERED} прошёл бы проверку одним статусом. */
    @ParameterizedTest
    @EnumSource(InboundStatus.class)
    void inStatus_selectsRowsReceivedBeforeNowMinusAge(InboundStatus status) {
        List<InboundMessage> aged = List.of(received(OneCStream.PRICE_LISTS));
        when(repository.findByStatusAndReceivedAtLessThan(status, NOW.minus(Duration.ofHours(1))))
                .thenReturn(aged);

        assertThat(agedMessages().inStatus(status, Duration.ofHours(1))).isSameAs(aged);
    }

    private AgedInboundMessages agedMessages() {
        return new AgedInboundMessages(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InboundMessage received(OneCStream stream) {
        return InboundMessage.receive(UUID.randomUUID(), stream, QUEUE, "{}", NOW);
    }
}
