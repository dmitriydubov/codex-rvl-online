package ru.projectx.contracts.support;

import static org.mockito.Mockito.mock;

import org.springframework.integration.leader.Context;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.leadership.LeaderLock;

public final class TestLeadership {

    public static final LeaderLock LOCK =
            new LeaderLock("contract-service", "contracts-background", "0b5f3c1e-7a2d-4e8b-9c6f-1d2e3f4a5b6c");

    private TestLeadership() {
    }

    public static BackgroundLeadership leader() {
        BackgroundLeadership leadership = new BackgroundLeadership(LOCK);
        leadership.onGranted(mock(Context.class));
        return leadership;
    }

    public static BackgroundLeadership standby() {
        return new BackgroundLeadership(LOCK);
    }
}
