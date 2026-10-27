package ru.projectx.contracts.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaderLockTest {

    @Test
    void newInstance_getsARandomCanonicalIdentifierAndKeepsGroupAndRole() {
        LeaderLock first = LeaderLock.forNewInstance("contract-service", "contracts-background");
        LeaderLock second = LeaderLock.forNewInstance("contract-service", "contracts-background");

        assertThat(first.group()).isEqualTo("contract-service");
        assertThat(first.role()).isEqualTo("contracts-background");
        assertThat(UUID.fromString(first.instanceId())).hasToString(first.instanceId());
        assertThat(first.instanceId()).isNotEqualTo(second.instanceId());
    }

    @Test
    void key_isTheSameUuidForTheSameRoleOnEveryInstance() {
        LeaderLock first = LeaderLock.forNewInstance("contract-service", "contracts-background");
        LeaderLock second = LeaderLock.forNewInstance("other-group", "contracts-background");

        assertThat(first.key()).isEqualTo(second.key());
        assertThat(UUID.fromString(first.key())).hasToString(first.key());
        assertThat(first.key()).isNotEqualTo(new LeaderLock("g", "another-role", "i").key());
    }
}
