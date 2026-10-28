package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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
import ru.projectx.datareference.service.leadership.BackgroundLeadership;
import ru.projectx.datareference.support.TestLeadership;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OneCConsumerPauseTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final String POLICIES = "1c-usk_to_rvl-online_policies";
    private static final String MKB10 = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String STOPPED = "1c-usk_to_rvl-online_programms-changes";
    private static final StorageOutage CONNECTION_LOST = new StorageOutage("08006", "DataAccessResourceFailureException");
    private static final StorageOutage LOCK_TIMEOUT = new StorageOutage("55P03", "CannotAcquireLockException");

    @Mock
    private ObjectProvider<RabbitListenerEndpointRegistry> registryProvider;

    @Mock
    private RabbitListenerEndpointRegistry registry;

    @Mock
    private IngestStorageProbe probe;

    @Mock
    private MessageListenerContainer policies;

    @Mock
    private MessageListenerContainer mkb10;

    @Mock
    private MessageListenerContainer stopped;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private BackgroundLeadership leadership = TestLeadership.leader();

    @BeforeEach
    void captureLog() {
        logged.start();
        pauseLog().addAppender(logged);
        containersLog().addAppender(logged);
        journalLog().addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        pauseLog().detachAppender(logged);
        containersLog().detachAppender(logged);
        journalLog().detachAppender(logged);
        logged.stop();
    }

    @BeforeEach
    void registry() {
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        when(registryProvider.getObject()).thenReturn(registry);
        when(registry.getListenerContainerIds()).thenReturn(Set.of(POLICIES, MKB10, STOPPED));
        when(registry.getListenerContainer(POLICIES)).thenReturn(policies);
        when(registry.getListenerContainer(MKB10)).thenReturn(mkb10);
        when(registry.getListenerContainer(STOPPED)).thenReturn(stopped);
        when(policies.isRunning()).thenReturn(true);
        when(mkb10.isRunning()).thenReturn(true);
        when(stopped.isRunning()).thenReturn(false);
        stopCompletesAtOnce(policies);
        stopCompletesAtOnce(mkb10);
    }

    @Test
    void pauseCounter_countsNewPausesOnly() {
        OneCConsumerPause pause = pause();

        pause.pause(CONNECTION_LOST);
        pause.pause(LOCK_TIMEOUT);
        assertThat(meters.get(OneCIngestMetrics.PAUSES).counter().count()).isEqualTo(1.0);

        when(probe.storageAvailable()).thenReturn(true);
        pause.resumeIfStorageAvailable();
        pause.pause(LOCK_TIMEOUT);
        assertThat(meters.get(OneCIngestMetrics.PAUSES).counter().count()).isEqualTo(2.0);
    }

    @Test
    void pause_stopsRunningConsumersOnlyAndRemembersThem() {
        OneCConsumerPause pause = pause();

        pause.pause(CONNECTION_LOST);

        verify(policies).stop(any(Runnable.class));
        verify(mkb10).stop(any(Runnable.class));
        verify(stopped, never()).stop(any(Runnable.class));
        verify(stopped, never()).stop();
        assertThat(pause.paused()).hasValueSatisfying(paused -> {
            assertThat(paused.outage()).isEqualTo(CONNECTION_LOST);
            assertThat(paused.since()).isEqualTo(NOW);
            assertThat(paused.queues()).containsExactlyInAnyOrder(MKB10, POLICIES);
        });
        assertThat(pause.consumersStopped()).isTrue();
    }

    @Test
    void secondOutageDuringPause_changesNothing() {
        OneCConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);

        pause.pause(LOCK_TIMEOUT);

        verify(policies).stop(any(Runnable.class));
        verify(mkb10).stop(any(Runnable.class));
        assertThat(pause.paused()).map(OneCConsumerPause.Paused::outage).hasValue(CONNECTION_LOST);
    }

    @Test
    void notPaused_checkDoesNotTouchDatabaseOrContainers() {
        OneCConsumerPause pause = pause();

        pause.resumeIfStorageAvailable();

        assertThat(pause.consumersStopped()).isTrue();
        verifyNoInteractions(probe, policies, mkb10, stopped);
    }

    @Test
    void storageStillUnavailable_keepsThePause() {
        OneCConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(false);

        pause.resumeIfStorageAvailable();

        verify(policies, never()).start();
        verify(mkb10, never()).start();
        assertThat(pause.paused()).isPresent();
    }

    @Test
    void storageBack_leaderStartsEveryStoppedConsumer() {
        OneCConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(true);

        pause.resumeIfStorageAvailable();

        verify(policies).start();
        verify(mkb10).start();
        verify(stopped).start();
        assertThat(pause.paused()).isEmpty();
    }

    @Test
    void storageBack_standbyLiftsThePauseWithoutStartingConsumers() {
        leadership = TestLeadership.standby();
        OneCConsumerPause standby = pause();
        standby.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(true);

        standby.resumeIfStorageAvailable();

        assertThat(standby.paused()).isEmpty();
        verify(policies, never()).start();
        verify(mkb10, never()).start();
        verify(stopped, never()).start();
    }

    @Test
    void roleGrantedWithoutPause_startsConsumers() {
        OneCConsumerPause pause = pause();

        pause.startUnlessPaused();

        verify(stopped).start();
        verify(policies).start();
        verify(mkb10).start();
    }

    @Test
    void roleGrantedRightAfterItWasLost_startsOnlyOnceTheStopHasFinished() {
        AtomicReference<Runnable> stopFinishes = new AtomicReference<>();
        doAnswer(invocation -> {
            stopFinishes.set(invocation.getArgument(0, Runnable.class));
            return null;
        }).when(policies).stop(any(Runnable.class));
        OneCConsumerPause pause = pause();
        pause.stopConsumersWithoutProbe();

        CompletableFuture<Void> granted = CompletableFuture.runAsync(pause::startUnlessPaused);

        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(5)).until(() -> !granted.isDone());
        verify(policies, never()).start();
        stopFinishes.get().run();
        granted.join();
        verify(policies).start();
    }

    @Test
    void roleGrantedDuringPause_leavesConsumersToTheStorageCheck() {
        OneCConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);

        pause.startUnlessPaused();

        verify(policies, never()).start();
        verify(mkb10, never()).start();
        verify(stopped, never()).start();
        assertThat(pause.paused()).isPresent();
    }

    @Test
    void unlessPaused_runsTheConsumersChangeOnlyWhileNotPaused() {
        OneCConsumerPause pause = pause();
        AtomicInteger runs = new AtomicInteger();

        pause.unlessPaused(runs::incrementAndGet);
        pause.pause(CONNECTION_LOST);
        pause.unlessPaused(runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    @Test
    void unlessPaused_holdsTheSameLockAsThePause() throws Exception {
        OneCConsumerPause pause = pause();
        Thread listener = new Thread(() -> pause.pause(CONNECTION_LOST));
        AtomicBoolean listenerWaited = new AtomicBoolean();

        pause.unlessPaused(() -> {
            listener.start();
            long changing = Thread.currentThread().threadId();
            await().atMost(Duration.ofSeconds(10)).until(() -> waitsInPauseForLockOf(listener, changing));
            listenerWaited.set(true);
        });

        assertThat(listenerWaited).isTrue();
        assertThat(listener.join(Duration.ofSeconds(10))).isTrue();
        assertThat(pause.paused()).isPresent();
    }

    @Test
    void lockLostWhileStoring_stopsConsumersWithoutTouchingTheDatabase() {
        OneCConsumerPause pause = pause();

        pause.stopConsumersWithoutProbe();

        verify(policies).stop(any(Runnable.class));
        verify(mkb10).stop(any(Runnable.class));
        verifyNoInteractions(probe);
        assertThat(pause.paused()).isEmpty();
    }

    @Test
    void roleLostWithStorageDown_pausesTheIngestWithItsCode() {
        when(probe.outage()).thenReturn(Optional.of(CONNECTION_LOST));
        OneCConsumerPause pause = pause();

        pause.stopConsumers();

        assertThat(pause.paused()).map(OneCConsumerPause.Paused::outage).hasValue(CONNECTION_LOST);
        verify(policies).stop(any(Runnable.class));
        verify(mkb10).stop(any(Runnable.class));
    }

    @Test
    void roleRevoked_stopsRunningConsumersAndKeepsThePause() {
        OneCConsumerPause pause = pause();

        pause.stopConsumers();

        verify(policies).stop(any(Runnable.class));
        verify(mkb10).stop(any(Runnable.class));
        verify(stopped, never()).stop(any(Runnable.class));
        assertThat(pause.paused()).isEmpty();
    }

    @Test
    void stopStillInProgress_postponesResume() {
        doAnswer(invocation -> null).when(mkb10).stop(any(Runnable.class));
        OneCConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(true);

        pause.resumeIfStorageAvailable();

        assertThat(pause.consumersStopped()).isFalse();
        verify(policies, never()).start();
        verify(mkb10, never()).start();
        assertThat(pause.paused()).isPresent();
    }

    @Test
    void outageAfterResume_pausesAgainWithTheNewReason() {
        OneCConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(true);
        pause.resumeIfStorageAvailable();

        pause.pause(LOCK_TIMEOUT);

        assertThat(pause.paused()).map(OneCConsumerPause.Paused::outage).hasValue(LOCK_TIMEOUT);
        verify(policies, times(2)).stop(any(Runnable.class));
    }

    @Test
    void outageFromAnotherThreadDuringResume_waitsAndStartsAFreshPause() throws Exception {
        OneCConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(true);
        List<String> events = new CopyOnWriteArrayList<>();
        recordStops(events, POLICIES, policies);
        recordStops(events, MKB10, mkb10);
        recordStart(events, MKB10, mkb10);
        Thread listener = new Thread(() -> pause.pause(LOCK_TIMEOUT));
        AtomicBoolean listenerWaitedForResume = new AtomicBoolean();
        doAnswer(invocation -> {
            events.add(started(POLICIES));
            listener.start();
            long resuming = Thread.currentThread().threadId();
            await().atMost(Duration.ofSeconds(10)).until(() -> waitsInPauseForLockOf(listener, resuming));
            listenerWaitedForResume.set(true);
            return null;
        }).when(policies).start();

        pause.resumeIfStorageAvailable();

        assertThat(listenerWaitedForResume).isTrue();
        assertThat(listener.join(Duration.ofSeconds(10))).isTrue();
        assertThat(events).hasSize(4);
        assertThat(events.subList(0, 2)).containsExactlyInAnyOrder(started(POLICIES), started(MKB10));
        assertThat(events.subList(2, 4)).containsExactlyInAnyOrder(stopped(POLICIES), stopped(MKB10));
        assertThat(pause.paused()).map(OneCConsumerPause.Paused::outage).hasValue(LOCK_TIMEOUT);
    }

    @Test
    void pauseReplacedDuringStorageCheck_isLeftForTheNextCheck() {
        OneCConsumerPause pause = pause();
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

        assertThat(pause.paused()).map(OneCConsumerPause.Paused::outage).hasValue(LOCK_TIMEOUT);
        verify(policies).start();
        verify(mkb10).start();
    }

    @Test
    void startFailureOfOneContainer_startsTheOthersAndReportsIt() {
        doThrow(new AmqpException("очередь пропала")).when(policies).start();
        OneCConsumerPause pause = pause();
        pause.pause(CONNECTION_LOST);
        when(probe.storageAvailable()).thenReturn(true);

        pause.resumeIfStorageAvailable();

        verify(mkb10).start();
        assertThat(pause.paused()).isEmpty();
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains(POLICIES, "не запущен"));
    }

    @Test
    void stopFailureOfOneContainer_doesNotWedgeThePause() {
        doThrow(new AmqpException("канал закрыт")).when(mkb10).stop(any(Runnable.class));
        OneCConsumerPause pause = pause();

        pause.pause(CONNECTION_LOST);

        verify(policies).stop(any(Runnable.class));
        assertThat(pause.consumersStopped()).isTrue();
        when(probe.storageAvailable()).thenReturn(true);
        pause.resumeIfStorageAvailable();
        verify(policies).start();
        verify(mkb10).start();
        assertThat(pause.paused()).isEmpty();
    }

    @Test
    void withoutRegistry_pauseIsRecordedAndNothingIsStopped() {
        when(registryProvider.getIfAvailable()).thenReturn(null);
        OneCConsumerPause pause = pause();

        pause.pause(CONNECTION_LOST);

        assertThat(pause.paused()).map(OneCConsumerPause.Paused::queues).hasValue(Set.of());
        when(probe.storageAvailable()).thenReturn(true);
        pause.resumeIfStorageAvailable();
        assertThat(pause.paused()).isEmpty();
        verifyNoInteractions(policies, mkb10, stopped);
    }

    @Test
    void queueWithoutContainer_failsNamingTheQueueAndRecordsNoPause() {
        when(registry.getListenerContainer(MKB10)).thenReturn(null);
        OneCConsumerPause pause = pause();

        assertThatIllegalStateException()
                .isThrownBy(() -> pause.pause(CONNECTION_LOST))
                .withMessageContaining(MKB10);

        assertThat(pause.paused()).isEmpty();
        verify(policies, never()).stop(any(Runnable.class));
    }

    private static void stopCompletesAtOnce(MessageListenerContainer container) {
        stopCompletesAtOnce(container, () -> { });
    }

    private static void stopCompletesAtOnce(MessageListenerContainer container, Runnable onStop) {
        doAnswer(invocation -> {
            onStop.run();
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(container).stop(any(Runnable.class));
    }

    private static void recordStart(List<String> events, String queue, MessageListenerContainer container) {
        doAnswer(invocation -> {
            events.add(started(queue));
            return null;
        }).when(container).start();
    }

    private static void recordStops(List<String> events, String queue, MessageListenerContainer container) {
        stopCompletesAtOnce(container, () -> events.add(stopped(queue)));
    }

    private static String started(String queue) {
        return "запущен " + queue;
    }

    private static String stopped(String queue) {
        return "остановлен " + queue;
    }

    private static boolean waitsInPauseForLockOf(Thread waiting, long ownerThreadId) {
        ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(waiting.threadId(), 1);
        return info != null && info.getLockOwnerId() == ownerThreadId
                && Arrays.stream(info.getStackTrace()).limit(1).anyMatch(OneCConsumerPauseTest::isPauseCall);
    }

    private static boolean isPauseCall(StackTraceElement frame) {
        return frame.getClassName().equals(OneCConsumerPause.class.getName()) && frame.getMethodName().equals("pause");
    }

    private OneCConsumerPause pause() {
        return new OneCConsumerPause(QueueContainersFixture.containers(registryProvider, leadership), probe,
                Clock.fixed(NOW, ZoneOffset.UTC), IngestTestMetrics.on(meters));
    }

    private static Logger containersLog() {
        return (Logger) LoggerFactory.getLogger(OneCQueueContainers.class);
    }

    private static Logger journalLog() {
        return (Logger) LoggerFactory.getLogger(OneCConsumerOutageJournal.class);
    }

    private static Logger pauseLog() {
        return (Logger) LoggerFactory.getLogger(OneCConsumerPause.class);
    }
}
