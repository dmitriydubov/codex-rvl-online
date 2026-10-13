package ru.projectx.contracts.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class ErasureStates {

    private final Map<UUID, String> beforeErasure = new HashMap<>();

    void remember(UUID messageId, String status, int attempts) {
        beforeErasure.put(messageId, state(status, attempts));
    }

    void clear() {
        beforeErasure.clear();
    }

    boolean unchanged(UUID messageId, String status, int attempts) {
        return state(status, attempts).equals(beforeErasure.get(messageId));
    }

    private static String state(String status, int attempts) {
        return status + "/" + attempts;
    }
}
