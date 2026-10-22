package ru.projectx.contracts.acceptance;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.assertj.core.api.SoftAssertions;
import org.jspecify.annotations.Nullable;

final class AcceptanceFields {

    private static final Map<String, Function<AcceptanceRecords.Acceptance, @Nullable Object>> FIELDS = Map.of(
            "contract_version", acceptance -> String.valueOf(acceptance.contractVersion()),
            "accepted_by", acceptance -> acceptance.acceptedBy().toString(),
            "accepted_at", acceptance -> acceptance.acceptedAt().toInstant(),
            "channel", AcceptanceRecords.Acceptance::channel,
            "ip_address", AcceptanceRecords.Acceptance::ipAddress,
            "user_agent", AcceptanceRecords.Acceptance::userAgent,
            "created_at", acceptance -> acceptance.createdAt().toInstant());

    private AcceptanceFields() {
    }

    static void check(SoftAssertions softly, AcceptanceRecords.Acceptance acceptance, String name,
            @Nullable String expected) {
        Function<AcceptanceRecords.Acceptance, @Nullable Object> field = Objects.requireNonNull(FIELDS.get(name),
                () -> "неизвестное поле записи акцепта: " + name);
        softly.assertThat(field.apply(acceptance)).as("поле %s записи акцепта", name)
                .isEqualTo(expectedOf(name, Objects.requireNonNullElse(expected, "")));
    }

    private static @Nullable Object expectedOf(String name, String expected) {
        if (expected.isEmpty()) {
            return null;
        }
        return name.endsWith("_at") ? OffsetDateTime.parse(expected).toInstant() : expected;
    }
}
