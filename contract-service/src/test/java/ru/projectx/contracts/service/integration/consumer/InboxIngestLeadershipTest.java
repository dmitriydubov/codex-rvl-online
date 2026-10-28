package ru.projectx.contracts.service.integration.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;
import ru.projectx.contracts.support.TestLeadership;

@ExtendWith(MockitoExtension.class)
class InboxIngestLeadershipTest {

    private static final long WAIT_MILLIS = 5_000;
    private static final long QUIET_MILLIS = 500;
    private static final String ROLE = TestLeadership.LOCK.role();

    @Mock
    private InboxConsumerPause consumers;

    private InboxIngestLeadership leadership;
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        leadershipLog().addAppender(logged);
    }

    @AfterEach
    void close() {
        leadership.close();
        leadershipLog().detachAppender(logged);
        logged.stop();
    }

    @Test
    void roleGranted_startsConsumersUnlessPaused() {
        leadership = new InboxIngestLeadership(consumers, TestLeadership.LOCK);

        leadership.granted(granted(ROLE));

        verify(consumers, timeout(WAIT_MILLIS)).startUnlessPaused();
        verifyNoMoreInteractions(consumers);
    }

    @Test
    void roleRevoked_stopsConsumers() {
        leadership = new InboxIngestLeadership(consumers, TestLeadership.LOCK);

        leadership.revoked(revoked(ROLE));

        verify(consumers, timeout(WAIT_MILLIS)).stopConsumers();
        verifyNoMoreInteractions(consumers);
    }

    @Test
    void eventsOfAnotherRole_changeNothing() {
        leadership = new InboxIngestLeadership(consumers, TestLeadership.LOCK);

        leadership.granted(granted("другая-роль"));
        leadership.revoked(revoked("другая-роль"));

        verify(consumers, after(QUIET_MILLIS).never()).startUnlessPaused();
        verify(consumers, never()).stopConsumers();
    }

    @Test
    void slowConsumerStart_doesNotHoldTheThreadThatKeepsTheLeaderLock() throws InterruptedException {
        CountDownLatch startGoesOn = new CountDownLatch(1);
        doAnswer(invocation -> startGoesOn.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)).when(consumers).startUnlessPaused();
        leadership = new InboxIngestLeadership(consumers, TestLeadership.LOCK);

        leadership.granted(granted(ROLE));

        assertThat(startGoesOn.getCount()).isEqualTo(1);
        startGoesOn.countDown();
        verify(consumers, timeout(WAIT_MILLIS)).startUnlessPaused();
    }

    @Test
    void roleChanges_areAppliedInTheOrderTheyCame() {
        List<String> applied = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> applied.add("запуск")).when(consumers).startUnlessPaused();
        doAnswer(invocation -> applied.add("остановка")).when(consumers).stopConsumers();
        leadership = new InboxIngestLeadership(consumers, TestLeadership.LOCK);

        leadership.granted(granted(ROLE));
        leadership.revoked(revoked(ROLE));
        leadership.granted(granted(ROLE));

        verify(consumers, timeout(WAIT_MILLIS).times(2)).startUnlessPaused();
        verify(consumers, timeout(WAIT_MILLIS)).stopConsumers();
        assertThat(applied).containsExactly("запуск", "остановка", "запуск");
    }

    @Test
    void failedRoleChange_isJournaledAndTheNextOneStillApplies() {
        doThrow(new IllegalStateException("нет контейнера")).when(consumers).startUnlessPaused();
        leadership = new InboxIngestLeadership(consumers, TestLeadership.LOCK);

        leadership.granted(granted(ROLE));
        leadership.revoked(revoked(ROLE));

        verify(consumers, timeout(WAIT_MILLIS)).stopConsumers();
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains(IllegalStateException.class.getName()));
    }

    @Test
    void roleChangeAfterShutdown_isIgnored() {
        leadership = new InboxIngestLeadership(consumers, TestLeadership.LOCK);
        leadership.close();

        leadership.revoked(revoked(ROLE));

        verify(consumers, after(QUIET_MILLIS).never()).stopConsumers();
    }

    private static OnGrantedEvent granted(String role) {
        return new OnGrantedEvent(new Object(), mock(Context.class), role);
    }

    private static OnRevokedEvent revoked(String role) {
        return new OnRevokedEvent(new Object(), mock(Context.class), role);
    }

    private static Logger leadershipLog() {
        return (Logger) LoggerFactory.getLogger(InboxIngestLeadership.class);
    }
}
