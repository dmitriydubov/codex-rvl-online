package ru.projectx.datareference.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaderLockTest {

    @Test
    void key_isTheNameBasedUuidOfTheRole_asTheLockRegistryStoresIt() {
        LeaderLock lock = new LeaderLock("data-reference", "onec-background", "0b5f3c1e-7a2d-4e8b-9c6f-1d2e3f4a5b6c");

        assertThat(lock.key())
                .isEqualTo(UUID.nameUUIDFromBytes("onec-background".getBytes(StandardCharsets.UTF_8)).toString());
    }

    @Test
    void newInstance_getsItsOwnIdWithinTheGroupAndRole() {
        LeaderLock first = LeaderLock.forNewInstance("data-reference", "onec-background");
        LeaderLock second = LeaderLock.forNewInstance("data-reference", "onec-background");

        assertThat(first.group()).isEqualTo("data-reference");
        assertThat(first.role()).isEqualTo("onec-background");
        assertThat(UUID.fromString(first.instanceId()).toString()).isEqualTo(first.instanceId());
        assertThat(first.instanceId()).isNotEqualTo(second.instanceId());
    }
}
