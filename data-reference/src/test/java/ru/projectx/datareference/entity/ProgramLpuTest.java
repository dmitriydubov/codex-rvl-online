package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Строка перечня учреждений принадлежит той программе, которая её заместила: по этой связи база хранит,
 * в каких санаториях обслуживают по программе.
 */
class ProgramLpuTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

    @Test
    void row_belongsToProgramThatReplacedItsList() {
        Program program = Program.create(UUID.randomUUID(),
                new Program.Details(null, null, "Программа РВЛ", null, false), NOW);
        Lpu lpu = Lpu.create(UUID.randomUUID(),
                new Lpu.Details("Санаторий", null, null, null, null, null, false), NOW);

        program.replaceAvailableLpu(List.of(new ProgramLpu.Row(lpu, null, null, null, 1, false,
                BigDecimal.ZERO, ProgramLpu.CoverageType.R)), NOW);

        assertThat(program.availableLpu().get(0).program()).isSameAs(program);
    }
}
