package ru.projectx.datareference.entity.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PolicyServiceLineTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final UUID PROGRAM = UUID.fromString("6a1f0c3e-1b2d-4e7a-9c1b-000000000001");

    @Test
    void of_keepsEveryValueOfTheRow() {
        var row = new PolicyServiceLine.Row(PROGRAM, 2, new BigDecimal("20.00"), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), true);

        PolicyServiceLine line = PolicyServiceLine.of(policy(), row, NOW);

        assertThat(line.externalProgramId()).isEqualTo(PROGRAM);
        assertThat(line.layerNumber()).isEqualTo(2);
        assertThat(line.copayment()).isEqualByComparingTo("20.00");
        assertThat(line.beginDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(line.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(line.availability()).isTrue();
        assertThat(line.id()).isNotNull();
    }

    @Test
    void of_keepsRowWithoutProgramAndUnavailable() {
        var row = new PolicyServiceLine.Row(null, 1, BigDecimal.ONE, null, null, false);

        PolicyServiceLine line = PolicyServiceLine.of(policy(), row, NOW);

        assertThat(line.externalProgramId()).isNull();
        assertThat(line.availability()).isFalse();
    }

    private static Policy policy() {
        var details = new Policy.Details(UUID.randomUUID(), "П-0001", "Тестов", "Тест", null,
                LocalDate.of(1990, 1, 1), null, null, LocalDate.of(2026, 1, 1), null, true, false);
        return Policy.create(UUID.randomUUID(), new Policy.Snapshot(details, List.of(), List.of()), NOW);
    }
}
