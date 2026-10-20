package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
import ru.projectx.datareference.support.TestLeadership;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OneCQueueContainersTest {

    private static final String POLICIES = "q-policies";
    private static final String MKB10 = "q-mkb10";

    @Mock
    private ObjectProvider<RabbitListenerEndpointRegistry> registryProvider;

    @Mock
    private RabbitListenerEndpointRegistry registry;

    @Mock
    private MessageListenerContainer policies;

    @Mock
    private MessageListenerContainer mkb10;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void policiesRunMkb10Stopped() {
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        when(registryProvider.getObject()).thenReturn(registry);
        when(registry.getListenerContainerIds()).thenReturn(Set.of(POLICIES, MKB10));
        when(registry.getListenerContainer(POLICIES)).thenReturn(policies);
        when(registry.getListenerContainer(MKB10)).thenReturn(mkb10);
        when(policies.isRunning()).thenReturn(true);
        when(mkb10.isRunning()).thenReturn(false);
        logged.start();
        containersLog().addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        containersLog().detachAppender(logged);
        logged.stop();
    }

    @Test
    void leaderWithIngestEnabled_expectsConsumers() {
        assertThat(QueueContainersFixture.containers(registryProvider, TestLeadership.leader()).consumersExpected())
                .isTrue();
    }

    @Test
    void standby_expectsNoConsumers() {
        assertThat(QueueContainersFixture.containers(registryProvider, TestLeadership.standby()).consumersExpected())
                .isFalse();
    }

    @Test
    void leaderWithIngestDisabledBySettings_expectsNoConsumers() {
        OneCQueueContainers containers = QueueContainersFixture.containers(registryProvider, TestLeadership.leader(),
                QueueContainersFixture.rabbitWithAutoStartup(false));

        assertThat(containers.consumersExpected()).isFalse();
    }

    @Test
    void leaderWithoutBroker_expectsNoConsumers() {
        OneCQueueContainers containers = QueueContainersFixture.containers(registryProvider, TestLeadership.leader(),
                QueueContainersFixture.withoutBroker());

        assertThat(containers.consumersExpected()).isFalse();
    }

    @Test
    void leader_startsEveryConsumer_aRunningContainerIgnoresTheStart() {
        QueueContainersFixture.containers(registryProvider, TestLeadership.leader()).startExpected();

        verify(mkb10).start();
        verify(policies).start();
    }

    @Test
    void consumersNotExpected_startsNothing() {
        QueueContainersFixture.containers(registryProvider, TestLeadership.standby()).startExpected();

        verify(mkb10, never()).start();
        verify(policies, never()).start();
    }

    @Test
    void startFailure_isReportedNamingTheQueue() {
        doThrow(new AmqpException("очередь пропала")).when(mkb10).start();

        QueueContainersFixture.containers(registryProvider, TestLeadership.leader()).startExpected();

        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains(MKB10, "не запущен"));
    }

    @Test
    void stopRunning_stopsOnlyRunningConsumersWithoutWaiting() {
        QueueContainersFixture.containers(registryProvider, TestLeadership.standby()).stopRunning();

        verify(policies).stop(any(Runnable.class));
        verify(mkb10, never()).stop(any(Runnable.class));
        verify(policies, never()).stop();
    }

    @Test
    void stopFailure_isReportedAndCountsAsStopped() {
        doThrow(new AmqpException("канал закрыт")).when(policies).stop(any(Runnable.class));
        AtomicBoolean stopped = new AtomicBoolean();

        QueueContainersFixture.containers(registryProvider, TestLeadership.leader()).stop(POLICIES, () -> stopped.set(true));

        assertThat(stopped).isTrue();
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains(POLICIES, "не остановлен"));
    }

    @Test
    void running_namesRunningContainersOnly() {
        assertThat(QueueContainersFixture.containers(registryProvider, TestLeadership.leader()).running())
                .containsExactly(POLICIES);
    }

    @Test
    void withoutRegistry_nothingRunsAndNothingIsStarted() {
        when(registryProvider.getIfAvailable()).thenReturn(null);
        OneCQueueContainers containers = QueueContainersFixture.containers(registryProvider, TestLeadership.leader());

        containers.startExpected();

        assertThat(containers.running()).isEmpty();
        verifyNoInteractions(policies, mkb10);
    }

    private static Logger containersLog() {
        return (Logger) LoggerFactory.getLogger(OneCQueueContainers.class);
    }
}
