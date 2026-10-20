package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OneCIngestLeadershipTest {

    private static final long WAIT_MILLIS = 5_000;
    private static final long QUIET_MILLIS = 500;

    @Mock
    private OneCConsumerPause consumers;

    private OneCIngestLeadership leadership;

    @AfterEach
    void close() {
        leadership.close();
    }

    @Test
    void roleGranted_startsConsumersUnlessPaused() {
        leadership = new OneCIngestLeadership(consumers);

        leadership.granted();

        verify(consumers, timeout(WAIT_MILLIS)).startUnlessPaused();
        verifyNoMoreInteractions(consumers);
    }

    @Test
    void roleRevoked_stopsConsumers() {
        leadership = new OneCIngestLeadership(consumers);

        leadership.revoked();

        verify(consumers, timeout(WAIT_MILLIS)).stopConsumers();
        verifyNoMoreInteractions(consumers);
    }

    @Test
    void slowConsumerStart_doesNotHoldTheThreadThatKeepsTheLeaderLock() throws InterruptedException {
        CountDownLatch startGoesOn = new CountDownLatch(1);
        doAnswer(invocation -> startGoesOn.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)).when(consumers).startUnlessPaused();
        leadership = new OneCIngestLeadership(consumers);

        leadership.granted();

        assertThat(startGoesOn.getCount()).isEqualTo(1);
        startGoesOn.countDown();
        verify(consumers, timeout(WAIT_MILLIS)).startUnlessPaused();
    }

    @Test
    void roleChanges_areAppliedInTheOrderTheyCame() {
        List<String> applied = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> applied.add("запуск")).when(consumers).startUnlessPaused();
        doAnswer(invocation -> applied.add("остановка")).when(consumers).stopConsumers();
        leadership = new OneCIngestLeadership(consumers);

        leadership.granted();
        leadership.revoked();
        leadership.granted();

        verify(consumers, timeout(WAIT_MILLIS).times(2)).startUnlessPaused();
        verify(consumers, timeout(WAIT_MILLIS)).stopConsumers();
        assertThat(applied).containsExactly("запуск", "остановка", "запуск");
    }

    @Test
    void roleChangeAfterShutdown_isIgnored() {
        leadership = new OneCIngestLeadership(consumers);
        leadership.close();

        leadership.revoked();
        leadership.granted();

        verify(consumers, after(QUIET_MILLIS).never()).stopConsumers();
        verify(consumers, never()).startUnlessPaused();
    }
}
