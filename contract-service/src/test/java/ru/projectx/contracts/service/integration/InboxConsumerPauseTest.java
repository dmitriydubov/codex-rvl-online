package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.support.TestLeadership;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InboxConsumerPauseTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final String TASKS = QueueContainersFixture.TASKS;
    private static final String LM_RESULTS = QueueContainersFixture.LM_RESULTS;
    private static final String KM_RESULTS = QueueContainersFixture.KM_RESULTS;
    private static final StorageOutage CONNECTION_LOST = new StorageOutage("08006", "DataAccessResourceFailureException");
    private static final StorageOutage LOCK_TIMEOUT = new StorageOutage("55P03", "CannotAcquireLockException");

    @Mock
    private ObjectProvider<RabbitListenerEndpointRegistry> registryProvider;

    @Mock
    private RabbitListenerEndpointRegistry registry;

    @Mock
    private InboxStorageProbe probe;

    @Mock
    private MessageListenerContainer tasks;

    @Mock
    private MessageListenerContainer lmResults;

    @Mock
    private MessageListenerContainer kmResults;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    private BackgroundLeadership leadership = TestLeadership.leader();

    @BeforeEach
    void captureLog() {
        logged.start();
        pauseLog().addAppender(logged);
        containersLog().addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        pauseLog().detachAppender(logged);
        containersLog().detachAppender(logged);
        logged.stop();
    }

    @BeforeEach
    void twoRunningConsumersAndOneStopped() {
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        when(registryProvider.getObject()).thenReturn(registry);
        when(registry.getListenerContainerIds()).thenReturn(Set.of(TASKS, LM_RESULTS, KM_RESULTS));
        when(registry.getListenerContainer(TASKS)).thenReturn(tasks);
        when(registry.getListenerContainer(LM_RESULTS)).thenReturn(lmResults);
        when(registry.getListenerContainer(KM_RESULTS)).thenReturn(kmResults);
        when(tasks.isRunning()).thenReturn(true);
        when(lmResults.isRunning()).thenReturn(true);
        when(kmResults.isRunning()).thenReturn(false);
        stopCompletesAtOnce(tasks);
        stopCompletesAtOnce(lmResults);
    }

    @Test
    void pause_stopsRunningConsumersOfBothBrokersAndRemembersThem() {
        InboxConsumerPause pause = pause();

        pause.pause(CONNECTION_LOST);

        verify(tasks).stop(any(Runnable.class));
        verify(lmResults).stop(any(Runnable.class));
        verify(kmResults, never()).stop(any(Runnable.class));
        assertThat(pause.paused()).hasValueSatisfying(paused -> {
            assertThat(paused.outage()).isEqualTo(CONNECTION_LOST);
            assertThat(paused.since()).isEqualTo(NOW);
            assertThat(paused.queues()).containsExactly(LM_RESULTS, TASKS);
        });
        assertThat(pause.consumersStopped()).isTrue();
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .contains("приостановлен", "DataAccessResourceFailureException", "08006"));
    }

    @Test
    void secondOutageDuringPause_changesNothing() {
        InboxConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);

        pause.pause(LOCK_TIMEOUT);

        verify(tasks).stop(any(Runnable.class));
        assertThat(pause.paused()).map(InboxConsumerPause.Paused::outage).hasValue(CONNECTION_LOST);
    }

    @Test
    void notPaused_checkDoesNotTouchDatabaseOrContainers() {
        InboxConsumerPause pause = pause();

        pause.resumeIfStorageAvailable();

        assertThat(pause.consumersStopped()).isTrue();
        verifyNoInteractions(probe, tasks, lmResults, kmResults);
    }

    @Test
    void storageStillUnavailable_keepsThePause() {
        InboxConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(false);

        pause.resumeIfStorageAvailable();

        verify(tasks, never()).start();
        verify(lmResults, never()).start();
        assertThat(pause.paused()).isPresent();
    }

    @Test
    void storageBack_leaderStartsEveryEnabledConsumer() {
        InboxConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(true);

        pause.resumeIfStorageAvailable();

        verify(tasks).start();
        verify(lmResults).start();
        verify(kmResults).start();
        assertThat(pause.paused()).isEmpty();
    }

    @Test
    void storageBack_standbyLiftsThePauseWithoutStartingConsumers() {
        leadership = TestLeadership.standby();
        InboxConsumerPause standby = pause();
        standby.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(true);

        standby.resumeIfStorageAvailable();

        assertThat(standby.paused()).isEmpty();
        verify(tasks, never()).start();
        verify(lmResults, never()).start();
        verify(kmResults, never()).start();
    }

    @Test
    void roleGrantedWithoutPause_startsConsumers() {
        InboxConsumerPause pause = pause();

        pause.startUnlessPaused();

        verify(tasks).start();
        verify(lmResults).start();
        verify(kmResults).start();
    }

    @Test
    void roleGrantedRightAfterItWasLost_startsOnlyOnceTheStopHasFinished() {
        AtomicReference<Runnable> stopFinishes = new AtomicReference<>();
        doAnswer(invocation -> {
            stopFinishes.set(invocation.getArgument(0, Runnable.class));
            return null;
        }).when(tasks).stop(any(Runnable.class));
        InboxConsumerPause pause = pause();
        pause.stopConsumersWithoutProbe();

        CompletableFuture<Void> granted = CompletableFuture.runAsync(pause::startUnlessPaused);

        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(5)).until(() -> !granted.isDone());
        verify(tasks, never()).start();
        stopFinishes.get().run();
        granted.join();
        verify(tasks).start();
    }

    @Test
    void roleGrantedDuringPause_leavesConsumersToTheStorageCheck() {
        InboxConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);

        pause.startUnlessPaused();

        verify(tasks, never()).start();
        verify(kmResults, never()).start();
        assertThat(pause.paused()).isPresent();
    }

    @Test
    void lockLostWhileStoring_stopsConsumersWithoutTouchingTheDatabase() {
        InboxConsumerPause pause = pause();

        pause.stopConsumersWithoutProbe();

        verify(tasks).stop(any(Runnable.class));
        verify(lmResults).stop(any(Runnable.class));
        verifyNoInteractions(probe);
        assertThat(pause.paused()).isEmpty();
    }

    @Test
    void roleLostWithStorageDown_pausesTheIngestWithItsCode() {
        when(probe.outage()).thenReturn(Optional.of(CONNECTION_LOST));
        InboxConsumerPause pause = pause();

        pause.stopConsumers();

        assertThat(pause.paused()).map(InboxConsumerPause.Paused::outage).hasValue(CONNECTION_LOST);
        verify(tasks).stop(any(Runnable.class));
    }

    @Test
    void roleRevokedWithStorageUp_stopsRunningConsumersWithoutPause() {
        InboxConsumerPause pause = pause();

        pause.stopConsumers();

        verify(tasks).stop(any(Runnable.class));
        verify(lmResults).stop(any(Runnable.class));
        verify(kmResults, never()).stop(any(Runnable.class));
        assertThat(pause.paused()).isEmpty();
    }

    @Test
    void stopStillInProgress_postponesResume() {
        doAnswer(invocation -> null).when(lmResults).stop(any(Runnable.class));
        InboxConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(true);

        pause.resumeIfStorageAvailable();

        assertThat(pause.consumersStopped()).isFalse();
        verify(tasks, never()).start();
        assertThat(pause.paused()).isPresent();
    }

    @Test
    void outageAfterResume_pausesAgainWithTheNewReason() {
        InboxConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(true);
        pause.resumeIfStorageAvailable();

        pause.pause(LOCK_TIMEOUT);

        assertThat(pause.paused()).map(InboxConsumerPause.Paused::outage).hasValue(LOCK_TIMEOUT);
        verify(tasks, times(2)).stop(any(Runnable.class));
    }

    @Test
    void pauseReplacedDuringStorageCheck_isLeftForTheNextCheck() {
        InboxConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        AtomicInteger checks = new AtomicInteger();
        when(probe.storageAvailable()).thenAnswer(invocation -> {
            if (checks.incrementAndGet() == 1) {
                pause.resumeIfStorageAvailable();
                pause.pause(LOCK_TIMEOUT);
            }
            return true;
        });

        pause.resumeIfStorageAvailable();

        assertThat(pause.paused()).map(InboxConsumerPause.Paused::outage).hasValue(LOCK_TIMEOUT);
        verify(tasks).start();
    }

    @Test
    void startFailureOfOneContainer_startsTheOthersAndReportsIt() {
        doThrow(new AmqpException("очередь пропала")).when(tasks).start();
        InboxConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(true);

        pause.resumeIfStorageAvailable();

        verify(lmResults).start();
        assertThat(pause.paused()).isEmpty();
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains(TASKS, "не запущен"));
    }

    @Test
    void stopFailureOfOneContainer_doesNotWedgeThePause() {
        doThrow(new AmqpException("канал закрыт")).when(lmResults).stop(any(Runnable.class));
        InboxConsumerPause pause = pause();

        pause.pause(CONNECTION_LOST);

        assertThat(pause.consumersStopped()).isTrue();
        when(probe.storageAvailable()).thenReturn(true);
        pause.resumeIfStorageAvailable();
        verify(tasks).start();
        assertThat(pause.paused()).isEmpty();
    }

    private static void stopCompletesAtOnce(MessageListenerContainer container) {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(container).stop(any(Runnable.class));
    }

    private InboxConsumerPause pause() {
        return new InboxConsumerPause(QueueContainersFixture.containers(registryProvider, leadership), probe,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Logger containersLog() {
        return (Logger) LoggerFactory.getLogger(InboxQueueContainers.class);
    }

    private static Logger pauseLog() {
        return (Logger) LoggerFactory.getLogger(InboxConsumerPause.class);
    }
}
