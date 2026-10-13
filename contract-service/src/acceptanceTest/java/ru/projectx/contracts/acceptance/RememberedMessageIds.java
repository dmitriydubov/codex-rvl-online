package ru.projectx.contracts.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class RememberedMessageIds {

    private final Map<String, UUID> byRecord = new HashMap<>();

    void remember(int ordinal, String requestId, UUID messageId) {
        byRecord.put(ordinal + "/" + requestId, messageId);
    }

    UUID of(int ordinal, String requestId) {
        return Optional.ofNullable(byRecord.get(ordinal + "/" + requestId))
                .orElseThrow(() -> new AssertionError("message_id записи №" + ordinal + " по заявке " + requestId
                        + " до публикации не запоминался"));
    }
}
