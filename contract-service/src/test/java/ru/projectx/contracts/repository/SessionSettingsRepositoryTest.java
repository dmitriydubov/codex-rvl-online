package ru.projectx.contracts.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class SessionSettingsRepositoryTest {

    @Autowired
    private SessionSettingsRepository sessionSettings;

    @Test
    void durations_readTheTimeoutsOfAPoolSessionInMilliseconds() {
        assertThat(sessionSettings.durations(
                List.of("lock_timeout", "statement_timeout", "idle_in_transaction_session_timeout")))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "lock_timeout", Duration.ofSeconds(5),
                        "statement_timeout", Duration.ofSeconds(30),
                        "idle_in_transaction_session_timeout", Duration.ofMinutes(1)));
    }

    @Test
    void parameterWithoutMillisecondUnit_isNotRead() {
        assertThat(sessionSettings.durations(List.of("lock_timeout", "max_connections")))
                .containsOnlyKeys("lock_timeout");
    }
}
