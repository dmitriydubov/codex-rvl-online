package ru.projectx.datareference.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.entity.ProgramLpu;

/**
 * Перечень услуг программы отдаёт сервису Договоров по каждой строке и учреждение, и условия обслуживания
 * в нём ровно так, как их прислала 1С: по этим условиям считается, на что застрахованный может рассчитывать.
 */
class ProgramResponseTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final UUID APPEAL_TYPE = UUID.fromString("5b2e1d4f-2c3e-4f8b-8d2c-000000000001");
    private static final LocalDate BEGIN = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void service_carriesLpuAndEveryConditionOfTheRow() {
        Lpu lpu = Lpu.create(UUID.randomUUID(),
                new Lpu.Details("Санаторий «Берёзка»", null, null, null, null, null, false), NOW);
        Program program = Program.create(UUID.randomUUID(),
                new Program.Details(null, null, "Программа РВЛ", null, false), NOW);
        program.replaceAvailableLpu(List.of(new ProgramLpu.Row(lpu, APPEAL_TYPE, BEGIN, END, 2, true,
                new BigDecimal("15.00"), ProgramLpu.CoverageType.D)), NOW);

        ProgramResponse.Service service = ProgramResponse.of(program).services().get(0);

        assertThat(service).isEqualTo(new ProgramResponse.Service(lpu.id(), lpu.externalId(),
                "Санаторий «Берёзка»", APPEAL_TYPE, BEGIN, END, 2, true, new BigDecimal("15.00"),
                ProgramLpu.CoverageType.D));
    }

    @Test
    void service_keepsDirectAttachmentOffWhenRowHasItOff() {
        Lpu lpu = Lpu.create(UUID.randomUUID(),
                new Lpu.Details("Санаторий", null, null, null, null, null, false), NOW);
        Program program = Program.create(UUID.randomUUID(),
                new Program.Details(null, null, "Программа РВЛ", null, false), NOW);
        program.replaceAvailableLpu(List.of(new ProgramLpu.Row(lpu, null, null, null, 1, false,
                BigDecimal.ZERO, ProgramLpu.CoverageType.R)), NOW);

        ProgramResponse.Service service = ProgramResponse.of(program).services().get(0);

        assertThat(service.directAttachment()).isFalse();
        assertThat(service.coverageType()).isEqualTo(ProgramLpu.CoverageType.R);
    }
}
