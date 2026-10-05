package ru.projectx.datareference.service.metrics;

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
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.StatusBacklog;
import ru.projectx.datareference.service.schedulers.AgedInboundMessages;
import ru.projectx.datareference.support.ImportSettings;

/**
 * Сборка снимка очереди разбора из ответов базы, без базы: каждая строка группировки становится парой «поток,
 * статус» со своим числом и самым давним сообщением, каждый подсчёт получает свой порог из настроек импорта,
 * момент снимка — текущее время часов. Как база считает, проверяет {@link InboundBacklogReadsTest}.
 *
 * <p>Пороги нарочно разные: перепутанный порог не совпадёт с заглушкой, и строгие заглушки Mockito уронят тест.
 */
@ExtendWith(MockitoExtension.class)
class InboundBacklogSnapshotTest {

    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(90);
    private static final Duration PAYLOAD_TTL = Duration.ofDays(31);
    private static final Duration QUARANTINE_TTL = Duration.ofDays(45);

    @Mock
    private InboundMessageRepository repository;

    @Mock
    private AgedInboundMessages agedMessages;

    @Test
    void snapshot_carriesEveryStatusRowCountsAndTheCurrentMoment() {
        Instant oldestNew = NOW.minus(Duration.ofMinutes(40));
        Instant oldestFailed = NOW.minus(Duration.ofDays(2));
        List<StatusBacklog> rows = List.of(
                row(OneCStream.POLICIES, InboundStatus.NEW, 2, oldestNew),
                row(OneCStream.MKB10, InboundStatus.FAILED, 1, oldestFailed));
        when(repository.countUnfinishedByStreamAndStatus()).thenReturn(rows);
        when(agedMessages.countTransfersBufferedLongerThan(STUCK_THRESHOLD)).thenReturn(3L);
        when(agedMessages.countPolicyPayloadsOlderThan(PAYLOAD_TTL)).thenReturn(4L);
        when(agedMessages.countQuarantineBodiesOlderThan(QUARANTINE_TTL)).thenReturn(5L);

        InboundBacklog backlog = reads().read();

        assertThat(backlog).isEqualTo(new InboundBacklog(
                List.of(new InboundBacklog.StatusCount(OneCStream.POLICIES, InboundStatus.NEW, 2, oldestNew),
                        new InboundBacklog.StatusCount(OneCStream.MKB10, InboundStatus.FAILED, 1, oldestFailed)),
                3, new InboundBacklog.Overdue(4, 5), NOW));
    }

    private InboundBacklogReads reads() {
        return new InboundBacklogReads(repository, agedMessages,
                ImportSettings.defaults().stuckTransferThreshold(STUCK_THRESHOLD).payloadTtl(PAYLOAD_TTL)
                        .quarantineTtl(QUARANTINE_TTL).build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static StatusBacklog row(OneCStream stream, InboundStatus status, long messages, Instant oldest) {
        StatusBacklog row = mock(StatusBacklog.class);
        when(row.getStream()).thenReturn(stream);
        when(row.getStatus()).thenReturn(status);
        when(row.getMessages()).thenReturn(messages);
        when(row.getOldestReceivedAt()).thenReturn(oldest);
        return row;
    }
}
