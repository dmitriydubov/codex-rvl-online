package ru.projectx.datareference.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.integration.leader.Context;

class BackgroundLeadershipTest {

    private static final LeaderLock LOCK =
            new LeaderLock("data-reference", "onec-background", "0b5f3c1e-7a2d-4e8b-9c6f-1d2e3f4a5b6c");

    private final BackgroundLeadership leadership = new BackgroundLeadership(LOCK);
    private final List<String> ran = new ArrayList<>();
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        leadershipLog().addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        leadershipLog().detachAppender(logged);
        logged.stop();
    }

    @Test
    void candidate_isNamedByTheLockRoleAndInstance() {
        assertThat(leadership.getRole()).isEqualTo("onec-background");
        assertThat(leadership.getId()).isEqualTo("0b5f3c1e-7a2d-4e8b-9c6f-1d2e3f4a5b6c");
    }

    @Test
    void newInstance_isNotLeaderUntilGranted() {
        assertThat(leadership.leader()).isFalse();

        leadership.onGranted(mock(Context.class));

        assertThat(leadership.leader()).isTrue();
    }

    @Test
    void revokedRole_isNoLongerLeader() {
        leadership.onGranted(mock(Context.class));

        leadership.onRevoked(mock(Context.class));

        assertThat(leadership.leader()).isFalse();
    }

    @Test
    void roleChanges_areJournaledWithTheInstance() {
        leadership.onGranted(mock(Context.class));
        leadership.onRevoked(mock(Context.class));

        assertThat(logged.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.INFO, Level.INFO);
        assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage).allSatisfy(message ->
                assertThat(message).contains(LOCK.instanceId()));
        assertThat(logged.list.getFirst().getFormattedMessage()).contains("стал ведущим");
        assertThat(logged.list.getLast().getFormattedMessage()).contains("больше не ведущий");
    }

    @Test
    void leader_runsThePass() {
        leadership.onGranted(mock(Context.class));

        leadership.runAsLeader(() -> ran.add("проход"));

        assertThat(ran).containsExactly("проход");
    }

    @Test
    void standby_skipsThePass() {
        leadership.runAsLeader(() -> ran.add("проход"));

        assertThat(ran).isEmpty();
    }

    @Test
    void runByRole_runsTheLeaderTaskOnLeader() {
        leadership.onGranted(mock(Context.class));

        leadership.runByRole(() -> ran.add("ведущий"), () -> ran.add("неведущий"));

        assertThat(ran).containsExactly("ведущий");
    }

    @Test
    void runByRole_runsTheStandbyTaskOnStandby() {
        leadership.runByRole(() -> ran.add("ведущий"), () -> ran.add("неведущий"));

        assertThat(ran).containsExactly("неведущий");
    }

    @Test
    void lockLostInTheMiddleOfThePass_endsThePassQuietly() {
        leadership.onGranted(mock(Context.class));
        logged.list.clear();

        leadership.runAsLeader(() -> {
            ran.add("первое сообщение");
            throw new LeadershipLostException(LOCK.instanceId());
        });

        assertThat(ran).containsExactly("первое сообщение");
        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("прерван", LOCK.instanceId());
        });
    }

    @Test
    void lockLostInTheMiddleOfThePass_givesTheRoleBackToTheElection() {
        Context election = mock(Context.class);
        leadership.onGranted(election);

        leadership.runAsLeader(() -> {
            throw new LeadershipLostException(LOCK.instanceId());
        });

        verify(election).yield();
    }

    @Test
    void stepDown_yieldsTheElectionThatGrantedTheRole() {
        Context election = mock(Context.class);
        leadership.onGranted(election);

        leadership.stepDown();

        verify(election).yield();
    }

    @Test
    void stepDownBeforeAnyGrant_changesNothing() {
        leadership.stepDown();

        assertThat(leadership.leader()).isFalse();
    }

    @Test
    void otherFailureOfThePass_isNotHidden() {
        leadership.onGranted(mock(Context.class));

        assertThatIllegalStateException().isThrownBy(() -> leadership.runAsLeader(() -> {
            throw new IllegalStateException("сбой прохода");
        }));
    }

    private static Logger leadershipLog() {
        return (Logger) LoggerFactory.getLogger(BackgroundLeadership.class);
    }
}
