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
    void brokerUnreachable_postponesTheStartWithoutReporting() {
        when(containers.stopped()).thenReturn(Set.of(POLICIES));
        when(presence.of(POLICIES)).thenReturn(OneCQueuePresence.OnBroker.UNREACHABLE);

        restart.restartStopped();

        verify(containers, never()).start(anyString());
        verifyNoInteractions(journal);
        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage()).contains(POLICIES, "отложен");
        });
    }

    @Test
    void everyStoppedConsumer_isHandledOnItsOwn() {
        when(containers.stopped()).thenReturn(Set.of(POLICIES, MKB10));
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
    void duringPause_theRestartIsLeftToThePauseGuard() {
        doAnswer(invocation -> null).when(pause).unlessPaused(any(Runnable.class));

        restart.restartStopped();

        verify(pause).unlessPaused(any(Runnable.class));
        verifyNoInteractions(containers, presence, journal);
    }

    private static Logger restartLog() {
        return (Logger) LoggerFactory.getLogger(OneCConsumerRestart.class);
    }
}
