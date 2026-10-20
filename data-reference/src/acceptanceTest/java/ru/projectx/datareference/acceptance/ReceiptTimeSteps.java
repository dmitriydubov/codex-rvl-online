package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.Before;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Тогда;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.datareference.repository.database.DatabaseClockRepository;

public class ReceiptTimeSteps {

    private static final Duration TWO_HOURS = Duration.ofHours(2);
    private static final String RAW_RECEIPT = "select received_at from inbound_message where message_id = :messageId";
    private static final String INBOX_RECEIPT = "select received_at from inbox where message_id = :messageId";
    private static final String QUARANTINE_RECEIPT = "select received_at from quarantine where message_id = :messageId";

    private final JdbcClient jdbc;
    private final DatabaseClockRepository databaseClock;
    private final StandClock clock;
    private final OneCInbox inbox;
    private Instant scenarioStartOnDatabaseClock = Instant.EPOCH;

    public ReceiptTimeSteps(JdbcClient jdbc, DatabaseClockRepository databaseClock, StandClock clock, OneCInbox inbox) {
        this.jdbc = jdbc;
        this.databaseClock = databaseClock;
        this.clock = clock;
        this.inbox = inbox;
    }

    @Before
    public void rememberDatabaseMomentBeforeScenario() {
        scenarioStartOnDatabaseClock = databaseClock.currentMoment();
    }

    @Тогда("момент приёма сообщения {string} в сырье и в журнале входящих сообщений — по часам {word}")
    public void rawAndInboxReceiptBy(String messageId, String clockName) {
        UUID id = UUID.fromString(messageId);
        receiptCheck(clockName).accept(List.of(receiptOf(RAW_RECEIPT, id), receiptOf(INBOX_RECEIPT, id)));
    }

    @Тогда("момент приёма карантинной записи {string} — по часам {word}")
    public void quarantineReceiptBy(String messageId, String clockName) {
        receiptCheck(clockName).accept(List.of(receiptOf(QUARANTINE_RECEIPT, UUID.fromString(messageId))));
    }

    @Дано("с момента приёма сообщений сценария прошло два часа")
    public void scenarioMessagesAgedTwoHours() {
        inbox.ageAll(TWO_HOURS);
    }

    private Consumer<List<Instant>> receiptCheck(String clockName) {
        Map<String, Consumer<List<Instant>>> checks = Map.of(
                "базы", this::takenByDatabaseClock,
                "экземпляра", this::takenByInstanceClock);
        Consumer<List<Instant>> check = checks.get(clockName);
        if (check == null) {
            throw new IllegalArgumentException("неизвестные часы: " + clockName);
        }
        return check;
    }

    private void takenByDatabaseClock(List<Instant> receipts) {
        Instant checkedAt = databaseClock.currentMoment();
        assertThat(receipts).as("моменты приёма по часам базы").allSatisfy(
                receipt -> assertThat(receipt).isBetween(scenarioStartOnDatabaseClock, checkedAt));
    }

    private void takenByInstanceClock(List<Instant> receipts) {
        assertThat(receipts).as("моменты приёма по часам экземпляра").containsOnly(clock.instant());
    }

    private Instant receiptOf(String query, UUID messageId) {
        return jdbc.sql(query).param("messageId", messageId).query(OffsetDateTime.class).single().toInstant();
    }
}
