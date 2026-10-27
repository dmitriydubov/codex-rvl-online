package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
import ru.projectx.contracts.support.TestLeadership;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InboxQueueContainersTest {

    private static final String TASKS = QueueContainersFixture.TASKS;
    private static final String LM_RESULTS = QueueContainersFixture.LM_RESULTS;
    private static final String KM_RESULTS = QueueContainersFixture.KM_RESULTS;
    private static final long QUIET_MILLIS = 300;

    @Mock
    private ObjectProvider<RabbitListenerEndpointRegistry> registryProvider;

    @Mock
    private RabbitListenerEndpointRegistry registry;

    @Mock
    private MessageListenerContainer tasks;

    @Mock
    private MessageListenerContainer lmResults;

    @Mock
    private MessageListenerContainer kmResults;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void tasksRunOneCResultsStopped() {
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        when(registryProvider.getObject()).thenReturn(registry);
        when(registry.getListenerContainerIds()).thenReturn(Set.of(TASKS, LM_RESULTS, KM_RESULTS));
        when(registry.getListenerContainer(TASKS)).thenReturn(tasks);
        when(registry.getListenerContainer(LM_RESULTS)).thenReturn(lmResults);
        when(registry.getListenerContainer(KM_RESULTS)).thenReturn(kmResults);
        when(tasks.isRunning()).thenReturn(true);
        when(lmResults.isRunning()).thenReturn(false);
        when(kmResults.isRunning()).thenReturn(false);
        logged.start();
        containersLog().addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        containersLog().detachAppender(logged);
        logged.stop();
    }

    @Test
    void leaderWithConsumersEnabled_expectsConsumers() {
        assertThat(QueueContainersFixture.containers(registryProvider, TestLeadership.leader()).consumersExpected())
                .isTrue();
    }

    @Test
    void standby_expectsNoConsumers() {
        assertThat(QueueContainersFixture.containers(registryProvider, TestLeadership.standby()).consumersExpected())
                .isFalse();
    }

    @Test
    void leaderWithBothBrokersSwitchedOff_expectsNoConsumers() {
        InboxQueueContainers containers = QueueContainersFixture.containers(registryProvider, TestLeadership.leader(),
                QueueContainersFixture.queues(false, false));

        assertThat(containers.consumersExpected()).isFalse();
    }

    @Test
    void leader_startsEveryEnabledConsumer_aRunningContainerIgnoresTheStart() {
        QueueContainersFixture.containers(registryProvider, TestLeadership.leader()).startExpected();

        verify(tasks).start();
        verify(lmResults).start();
        verify(kmResults).start();
    }

    @Test
    void leaderWithOneCListenersSwitchedOff_startsOnlyTheTaskQueue() {
        QueueContainersFixture.containers(registryProvider, TestLeadership.leader(),
                QueueContainersFixture.queues(true, false)).startExpected();

        verify(tasks).start();
        verify(lmResults, never()).start();
        verify(kmResults, never()).start();
    }

    @Test
    void standby_startsNothing() {
        QueueContainersFixture.containers(registryProvider, TestLeadership.standby()).startExpected();

        verify(tasks, never()).start();
        verify(lmResults, never()).start();
        verify(kmResults, never()).start();
    }

    @Test
    void startFailure_isReportedNamingTheQueue() {
        doThrow(new AmqpException("очередь пропала")).when(lmResults).start();

        QueueContainersFixture.containers(registryProvider, TestLeadership.leader()).startExpected();

        verify(kmResults).start();
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains(LM_RESULTS, "не запущен"));
    }

    @Test
    void stopRunning_stopsOnlyRunningConsumersWithoutWaiting() {
        QueueContainersFixture.containers(registryProvider, TestLeadership.standby()).stopRunning();

        verify(tasks).stop(any(Runnable.class));
        verify(lmResults, never()).stop(any(Runnable.class));
        verify(tasks, never()).stop();
    }

    @Test
    void stopFailure_isReportedAndCountsAsStopped() {
        doThrow(new AmqpException("канал закрыт")).when(tasks).stop(any(Runnable.class));
        AtomicBoolean stopped = new AtomicBoolean();

        QueueContainersFixture.containers(registryProvider, TestLeadership.leader()).stop(TASKS, () -> stopped.set(true));

        assertThat(stopped).isTrue();
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains(TASKS, "не остановлен"));
    }

    @Test
    void awaitStops_waitsUntilTheStoppingContainerHasStopped() {
        AtomicReference<Runnable> stopFinishes = new AtomicReference<>();
        doAnswer(invocation -> {
            stopFinishes.set(invocation.getArgument(0, Runnable.class));
            return null;
        }).when(tasks).stop(any(Runnable.class));
        InboxQueueContainers containers = QueueContainersFixture.containers(registryProvider, TestLeadership.leader());
        containers.stopRunning();

        CompletableFuture<Void> waiting = CompletableFuture.runAsync(containers::awaitStops);

        await().during(Duration.ofMillis(QUIET_MILLIS)).atMost(Duration.ofSeconds(5)).until(() -> !waiting.isDone());
        stopFinishes.get().run();
        waiting.join();
        assertThat(logged.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }

    @Test
    void stopThatOutlivesTheTimeout_isReportedAndNoLongerAwaited() {
        doAnswer(invocation -> null).when(tasks).stop(any(Runnable.class));
        InboxQueueContainers containers =
                QueueContainersFixture.containersStoppingWithin(registryProvider, Duration.ofMillis(QUIET_MILLIS));
        containers.stopRunning();

        containers.awaitStops();

        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains(TASKS, "не остановились"));
    }

    @Test
    void awaitStopsWithNothingStopping_returnsAtOnceWithoutWarning() {
        QueueContainersFixture.containers(registryProvider, TestLeadership.leader()).awaitStops();

        assertThat(logged.list).isEmpty();
    }

    @Test
    void running_namesRunningContainersOnly() {
        assertThat(QueueContainersFixture.containers(registryProvider, TestLeadership.leader()).running())
                .containsExactly(TASKS);
    }

    @Test
    void withoutRegistry_nothingRunsAndNothingIsStopped() {
        when(registryProvider.getIfAvailable()).thenReturn(null);
        InboxQueueContainers containers = QueueContainersFixture.containers(registryProvider, TestLeadership.leader());

        containers.stopRunning();

        assertThat(containers.running()).isEmpty();
        verifyNoInteractions(tasks, lmResults, kmResults);
    }

    @Test
    void queueWithoutContainer_failsNamingTheQueue() {
        when(registry.getListenerContainer(LM_RESULTS)).thenReturn(null);
        InboxQueueContainers containers = QueueContainersFixture.containers(registryProvider, TestLeadership.leader());

        org.assertj.core.api.Assertions.assertThatIllegalStateException()
                .isThrownBy(containers::running)
                .withMessageContaining(LM_RESULTS);
    }

    private static Logger containersLog() {
        return (Logger) LoggerFactory.getLogger(InboxQueueContainers.class);
    }
}
