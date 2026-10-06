package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Строка перечня учреждений принадлежит той программе, которая её заместила: по этой связи база хранит,
 * в каких санаториях обслуживают по программе. Условия обслуживания — период, слой, прикрепление, доля
 * сооплаты и часть покрытия — хранятся как пришли.
 */
class ProgramLpuTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final UUID APPEAL_TYPE = UUID.fromString("1c000000-0000-4000-8000-000000000041");

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

    @Test
    void of_keepsEveryValueOfTheRow() {
        Lpu lpu = lpu();
        var row = new ProgramLpu.Row(lpu, APPEAL_TYPE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 2,
                true, new BigDecimal("15.50"), ProgramLpu.CoverageType.D);

        ProgramLpu line = ProgramLpu.of(program(), row, NOW);

        assertThat(line.lpu()).isSameAs(lpu);
        assertThat(line.externalAppealTypeId()).isEqualTo(APPEAL_TYPE);
        assertThat(line.beginDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(line.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(line.layerNumber()).isEqualTo(2);
        assertThat(line.directAttachment()).isTrue();
        assertThat(line.percentCopayment()).isEqualByComparingTo("15.50");
        assertThat(line.coverageType()).isEqualTo(ProgramLpu.CoverageType.D);
    }

    @Test
    void of_keepsRiskCoverageAndIndirectAttachment() {
        var row = new ProgramLpu.Row(lpu(), null, null, null, 1, false, BigDecimal.ZERO,
                ProgramLpu.CoverageType.R);

        ProgramLpu line = ProgramLpu.of(program(), row, NOW);

        assertThat(line.coverageType()).isEqualTo(ProgramLpu.CoverageType.R);
        assertThat(line.directAttachment()).isFalse();
    }

    private static Program program() {
        return Program.create(UUID.randomUUID(), new Program.Details(null, null, "Программа РВЛ", null, false), NOW);
    }

    private static Lpu lpu() {
        return Lpu.create(UUID.randomUUID(), new Lpu.Details("Санаторий", null, null, null, null, null, false), NOW);
    }
}
