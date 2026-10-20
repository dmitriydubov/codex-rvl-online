package ru.projectx.datareference.support;

import static org.mockito.Mockito.mock;

import org.springframework.integration.leader.Context;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;
import ru.projectx.datareference.service.leadership.LeaderLock;

public final class TestLeadership {

    public static final LeaderLock LOCK =
            new LeaderLock("data-reference", "onec-background", "0b5f3c1e-7a2d-4e8b-9c6f-1d2e3f4a5b6c");

    public static BackgroundLeadership leader() {
        BackgroundLeadership leadership = new BackgroundLeadership(LOCK);
        leadership.onGranted(mock(Context.class));
        return leadership;
    }

    public static BackgroundLeadership standby() {
        return new BackgroundLeadership(LOCK);
    }

    private TestLeadership() {
    }
}
