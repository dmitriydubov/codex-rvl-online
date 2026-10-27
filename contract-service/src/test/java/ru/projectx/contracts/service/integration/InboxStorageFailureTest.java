package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import ru.projectx.contracts.service.leadership.LeadershipLostException;

class InboxStorageFailureTest {

    @Test
    void dataExceptionClass_isPoisonWithItsCode() {
        assertThat(InboxStorageFailure.of(new DataIntegrityViolationException("x", new SQLException("y", "22P05"))))
                .isEqualTo(new InboxStorageFailure.Poison("22P05"));
        assertThat(InboxStorageFailure.of(new DataIntegrityViolationException("x", new SQLException("y", "22021"))))
                .isEqualTo(new InboxStorageFailure.Poison("22021"));
    }

    @Test
    void uniqueViolation_isADuplicateRace() {
        assertThat(InboxStorageFailure.of(new DataIntegrityViolationException("x", new SQLException("y", "23505"))))
                .isEqualTo(new InboxStorageFailure.DuplicateRace());
    }

    @Test
    void lostLeaderLock_isLeadershipLostWhateverTheCause() {
        assertThat(InboxStorageFailure.of(new LeadershipLostException("0b5f3c1e")))
                .isEqualTo(new InboxStorageFailure.LeadershipLost());
    }

    @Test
    void anyOtherCode_orNoCode_isAnOutageWithTypeAndCode() {
        assertThat(InboxStorageFailure.of(new DataAccessResourceFailureException("x", new SQLException("y", "08006"))))
                .isEqualTo(new InboxStorageFailure.Outage(
                        new StorageOutage("08006", DataAccessResourceFailureException.class.getName())));
        assertThat(InboxStorageFailure.of(new IllegalStateException("x", new SQLException("y", "23503"))))
                .isEqualTo(new InboxStorageFailure.Outage(new StorageOutage("23503", IllegalStateException.class.getName())));
        assertThat(InboxStorageFailure.of(new IllegalStateException("x")))
                .isEqualTo(new InboxStorageFailure.Outage(new StorageOutage("нет", IllegalStateException.class.getName())));
    }

    @Test
    void deepestCode_wins() {
        SQLException deep = new SQLException("deep", "22P02");
        SQLException shallow = new SQLException("shallow", "XX000", deep);

        assertThat(InboxStorageFailure.of(new IllegalStateException("x", shallow)))
                .isEqualTo(new InboxStorageFailure.Poison("22P02"));
    }
}
