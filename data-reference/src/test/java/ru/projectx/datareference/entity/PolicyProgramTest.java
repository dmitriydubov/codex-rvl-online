package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Строка программы полиса хранит значения табличной части как пришли: период, страховые суммы и премии по обеим
 * частям и лимит на лечение. Ничего не пересчитывается и не подставляется по умолчанию.
 */
class PolicyProgramTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final UUID PROGRAM = UUID.fromString("6a1f0c3e-1b2d-4e7a-9c1b-000000000001");

    @Test
    void of_keepsEveryValueOfTheRow() {
        var row = new PolicyProgram.Row(PROGRAM, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("100000.00"), new BigDecimal("200000.00"), new BigDecimal("3000.00"),
                new BigDecimal("4000.00"), new BigDecimal("50000.00"), true);

        PolicyProgram program = PolicyProgram.of(policy(), row, NOW);

        assertThat(program.externalProgramId()).isEqualTo(PROGRAM);
        assertThat(program.beginDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(program.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(program.amount()).isEqualByComparingTo("100000.00");
        assertThat(program.amountD()).isEqualByComparingTo("200000.00");
        assertThat(program.premium()).isEqualByComparingTo("3000.00");
        assertThat(program.premiumD()).isEqualByComparingTo("4000.00");
        assertThat(program.limitAmount()).isEqualByComparingTo("50000.00");
        assertThat(program.availability()).isTrue();
        assertThat(program.id()).isNotNull();
    }

    @Test
    void of_keepsUnavailableRowUnavailable() {
        var row = new PolicyProgram.Row(PROGRAM, LocalDate.of(2026, 1, 1), null, null, null, null, null,
                BigDecimal.TEN, false);

        assertThat(PolicyProgram.of(policy(), row, NOW).availability()).isFalse();
    }

    private static Policy policy() {
        var details = new Policy.Details(UUID.randomUUID(), "П-0001", "Тестов", "Тест", null,
                LocalDate.of(1990, 1, 1), null, null, LocalDate.of(2026, 1, 1), null, true, false);
        return Policy.create(UUID.randomUUID(), new Policy.Snapshot(details, List.of(), List.of()), NOW);
    }
}
