package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OneCConsumerRestartTest {

    private static final String POLICIES = "q-policies";
    private static final String MKB10 = "q-mkb10";
    private static final OneCConsumerPause.Paused PAUSED = new OneCConsumerPause.Paused(
            new StorageOutage("08006", "DataAccessResourceFailureException"),
            Instant.parse("2026-10-28T12:00:00Z"),
            Set.of(POLICIES));

    @Mock
    private OneCConsumerPause pause;

    @Mock
    private OneCQueueContainers containers;

    @Mock
    private OneCQueuePresence presence;

    @Mock
    private OneCConsumerOutageJournal journal;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    private OneCConsumerRestart restart;

    @BeforeEach
    void captureLog() {
        logged.start();
        restartLog().setLevel(Level.DEBUG);
        restartLog().addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        restartLog().detachAppender(logged);
        restartLog().setLevel(null);
        logged.stop();
    }

    @BeforeEach
    void notPaused() {
        when(pause.paused()).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(pause).unlessPaused(any(Runnable.class));
        restart = new OneCConsumerRestart(pause, containers, presence, journal);
    }

    @Test
    void stoppedConsumerWhoseQueueIsBack_isStarted() {
        when(containers.stopped()).thenReturn(Set.of(POLICIES));
        when(presence.of(POLICIES)).thenReturn(OneCQueuePresence.OnBroker.PRESENT);

        restart.restartStopped();

        verify(containers).start(POLICIES);
        verifyNoInteractions(journal);
    }

    @Test
    void stoppedConsumerWhoseQueueIsStillMissing_isReportedNotStarted() {
        when(containers.stopped()).thenReturn(Set.of(POLICIES));
        when(presence.of(POLICIES)).thenReturn(OneCQueuePresence.OnBroker.ABSENT);

        restart.restartStopped();

        verify(journal).startFailed(POLICIES, OneCConsumerRestart.QUEUE_MISSING);
        verify(containers, never()).start(anyString());
    }

    @Test
    void brokerUnreachable_postponesTheWholePassWithoutReporting() {
        when(containers.stopped()).thenReturn(inOrder(POLICIES, MKB10));
        when(presence.of(POLICIES)).thenReturn(OneCQueuePresence.OnBroker.UNREACHABLE);

        restart.restartStopped();

        verify(containers, never()).start(anyString());
        verify(presence, never()).of(MKB10);
        verifyNoInteractions(journal);
        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage()).contains("отложен");
        });
    }

    @Test
    void brokerRefusesTheQueue_isReportedNotStartedAndTheNextQueueIsProbed() {
        when(containers.stopped()).thenReturn(inOrder(POLICIES, MKB10));
        when(presence.of(POLICIES)).thenReturn(OneCQueuePresence.OnBroker.REFUSED);
        when(presence.of(MKB10)).thenReturn(OneCQueuePresence.OnBroker.PRESENT);

        restart.restartStopped();

        verify(journal).startFailed(POLICIES, OneCConsumerRestart.QUEUE_REFUSED);
        verify(containers, never()).start(POLICIES);
        verify(containers).start(MKB10);
        assertThat(logged.list).isEmpty();
    }

    @Test
    void everyStoppedConsumer_isHandledOnItsOwn() {
        when(containers.stopped()).thenReturn(inOrder(POLICIES, MKB10));
        when(presence.of(POLICIES)).thenReturn(OneCQueuePresence.OnBroker.ABSENT);
        when(presence.of(MKB10)).thenReturn(OneCQueuePresence.OnBroker.PRESENT);

        restart.restartStopped();

        verify(containers).start(MKB10);
        verify(containers, never()).start(POLICIES);
        verify(journal).startFailed(POLICIES, OneCConsumerRestart.QUEUE_MISSING);
        verify(journal, never()).startFailed(MKB10, OneCConsumerRestart.QUEUE_MISSING);
    }

    @Test
    void nothingStopped_touchesNeitherBrokerNorJournal() {
        when(containers.stopped()).thenReturn(Set.of());

        restart.restartStopped();

        verifyNoInteractions(presence, journal);
        verify(containers, never()).start(anyString());
    }

    @Test
    void duringPause_nothingIsProbedOrStarted() {
        when(pause.paused()).thenReturn(Optional.of(PAUSED));

        restart.restartStopped();

        verify(pause, never()).unlessPaused(any(Runnable.class));
        verifyNoInteractions(containers, presence, journal);
    }

    @Test
    void pauseThatBeganAfterTheProbe_cancelsTheStart() {
        when(containers.stopped()).thenReturn(Set.of(POLICIES));
        when(presence.of(POLICIES)).thenReturn(OneCQueuePresence.OnBroker.PRESENT);
        doAnswer(invocation -> null).when(pause).unlessPaused(any(Runnable.class));

        restart.restartStopped();

        verify(pause).unlessPaused(any(Runnable.class));
        verify(containers, never()).start(anyString());
    }

    @Test
    void consumerStartedByAnotherPathAfterTheProbe_isNotStartedAgain() {
        when(containers.stopped()).thenReturn(Set.of(POLICIES)).thenReturn(Set.of());
        when(presence.of(POLICIES)).thenReturn(OneCQueuePresence.OnBroker.PRESENT);

        restart.restartStopped();

        verify(containers, never()).start(anyString());
    }

    @Test
    void queueThatVanishedAgainIsStillStarted_theContainerReportsItsOwnFailure() {
        when(containers.stopped()).thenReturn(inOrder(POLICIES, MKB10));
        when(presence.of(POLICIES)).thenReturn(OneCQueuePresence.OnBroker.PRESENT);
        when(presence.of(MKB10)).thenReturn(OneCQueuePresence.OnBroker.PRESENT);

        restart.restartStopped();

        verify(containers).start(POLICIES);
        verify(containers).start(MKB10);
        verifyNoInteractions(journal);
    }

    @Test
    void startedConsumer_doesNotStopThePassForTheNextQueue() {
        when(containers.stopped()).thenReturn(inOrder(POLICIES, MKB10));
        when(presence.of(POLICIES)).thenReturn(OneCQueuePresence.OnBroker.PRESENT);
        when(presence.of(MKB10)).thenReturn(OneCQueuePresence.OnBroker.ABSENT);

        restart.restartStopped();

        verify(containers).start(POLICIES);
        verify(journal).startFailed(MKB10, OneCConsumerRestart.QUEUE_MISSING);
    }

    @Test
    void nothingBackOnBroker_doesNotTakeThePauseMonitor() {
        when(containers.stopped()).thenReturn(Set.of(POLICIES));
        when(presence.of(POLICIES)).thenReturn(OneCQueuePresence.OnBroker.ABSENT);

        restart.restartStopped();

        verify(pause, never()).unlessPaused(any(Runnable.class));
    }

    private static Set<String> inOrder(String... queues) {
        return new LinkedHashSet<>(List.of(queues));
    }

    private static Logger restartLog() {
        return (Logger) LoggerFactory.getLogger(OneCConsumerRestart.class);
    }
}
