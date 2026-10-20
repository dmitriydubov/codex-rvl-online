package ru.projectx.datareference.service.leadership;

import java.util.UUID;
import org.springframework.integration.util.UUIDConverter;

public record LeaderLock(String group, String role, String instanceId) {

    static LeaderLock forNewInstance(String group, String role) {
        return new LeaderLock(group, role, UUID.randomUUID().toString());
    }

    public String key() {
        return String.valueOf(UUIDConverter.getUUID(role));
    }
}
