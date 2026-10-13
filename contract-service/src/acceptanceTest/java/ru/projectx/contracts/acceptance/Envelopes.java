package ru.projectx.contracts.acceptance;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.SoftAssertions;
import tools.jackson.databind.JsonNode;

final class Envelopes {

    private static final Set<String> FIELDS = Set.of("topic", "event_type", "schema_version", "source",
            "correlation_id", "entity_version", "created_at");

    private Envelopes() {
    }

    static void check(JsonNode meta, Map<String, String> expected) {
        StepTables.onlyKnown(expected, FIELDS);
        SoftAssertions softly = new SoftAssertions();
        for (Map.Entry<String, String> cell : expected.entrySet()) {
            if ("created_at".equals(cell.getKey())) {
                softly.assertThat(OffsetDateTime.parse(meta.path("created_at").stringValue()).toInstant())
                        .as("created_at").isEqualTo(OffsetDateTime.parse(cell.getValue()).toInstant());
            }
            else {
                softly.assertThat(meta.path(cell.getKey()).asString()).as(cell.getKey()).isEqualTo(cell.getValue());
            }
        }
        softly.assertAll();
    }
}
