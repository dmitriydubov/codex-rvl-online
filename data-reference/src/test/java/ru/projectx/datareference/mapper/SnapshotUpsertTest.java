package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnapshotUpsertTest {

    private static final UUID EXTERNAL_ID = UUID.fromString("5b0d2c1e-3f4a-4b5c-8d6e-7f8091a2b3c4");
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    private final List<Card> stored = new ArrayList<>();

    @Test
    void unknownRecord_isCreatedFromTheSnapshot_andSaved() {
        ImportResult result = upsert(Optional.empty()).apply(EXTERNAL_ID, "новый снимок", NOW);

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(stored).singleElement().satisfies(card -> {
            assertThat(card.externalId).isEqualTo(EXTERNAL_ID);
            assertThat(card.snapshot).isEqualTo("новый снимок");
            assertThat(card.changedAt).isEqualTo(NOW);
            assertThat(card.created).isTrue();
        });
    }

    @Test
    void knownRecord_getsTheSnapshot_andIsSavedItself() {
        Card known = new Card(EXTERNAL_ID, "старый снимок", NOW.minusSeconds(60), false);

        ImportResult result = upsert(Optional.of(known)).apply(EXTERNAL_ID, "новый снимок", NOW);

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(stored).singleElement().isSameAs(known);
        assertThat(known.snapshot).isEqualTo("новый снимок");
        assertThat(known.changedAt).isEqualTo(NOW);
    }

    private SnapshotUpsert<Card, String> upsert(Optional<Card> found) {
        return new SnapshotUpsert<>(
                externalId -> found.filter(card -> card.externalId.equals(externalId)),
                (externalId, snapshot, now) -> new Card(externalId, snapshot, now, true),
                Card::refresh,
                stored::add);
    }

    private static final class Card {

        private final UUID externalId;
        private final boolean created;
        private String snapshot;
        private Instant changedAt;

        private Card(UUID externalId, String snapshot, Instant changedAt, boolean created) {
            this.externalId = externalId;
            this.snapshot = snapshot;
            this.changedAt = changedAt;
            this.created = created;
        }

        private void refresh(String newSnapshot, Instant now) {
            this.snapshot = newSnapshot;
            this.changedAt = now;
        }
    }
}
