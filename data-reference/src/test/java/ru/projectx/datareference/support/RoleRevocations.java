package ru.projectx.datareference.support;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.event.EventListener;
import org.springframework.integration.leader.event.OnRevokedEvent;

public class RoleRevocations {

    private final AtomicInteger revoked = new AtomicInteger();

    @EventListener
    public void revoked(OnRevokedEvent event) {
        revoked.incrementAndGet();
    }

    public int count() {
        return revoked.get();
    }

    public void reset() {
        revoked.set(0);
    }
}
