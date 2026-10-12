package ru.projectx.datareference.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionOperations;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.entity.ProgramLpu;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class ProgramLpuOrderTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final LocalDate JANUARY = LocalDate.of(2026, 1, 1);
    private static final LocalDate JULY = LocalDate.of(2026, 7, 1);
    private static final UUID APPEAL_TYPE_FIRST = UUID.fromString("3b1f6c2e-8d47-4a95-b0e3-71c9d5a2f468");
    private static final UUID APPEAL_TYPE_SECOND = UUID.fromString("c7e2a9d4-1f63-4b8e-9a05-d4b8e6f13c27");

    @Autowired
    private ProgramRepository programs;

    @Autowired
    private LpuRepository lpus;

    @Autowired
    private ProgramLpuRepository programRows;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private Lpu alpha;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
        alpha = lpu("6d0e4a7b-2c95-4f18-8b3d-e9a1c7f50d62", "Санаторий «Альфа»");
    }

    @Test
    void lpuName_comesFirst() {
        Lpu beta = lpu("0f9c3e81-75ab-4d26-a4c8-5b2e19d7f0a3", "Санаторий «Бета»");

        UUID programId = program(row(beta), row(alpha));

        assertThat(ordered(programId, row -> row.lpu().name())).containsExactly("Санаторий «Альфа»", "Санаторий «Бета»");
    }

    @Test
    void sameName_goesByLpuGuidAsWritten_notAsSignedNumber() {
        Lpu twin = lpu("b515b8fc-d13c-4b0b-aae3-33e0ca86ff1c", "Санаторий «Альфа»");

        UUID programId = program(row(twin).layer(1), row(alpha).layer(2));

        assertThat(ordered(programId, row -> row.lpu().externalId().toString()))
                .containsExactly("6d0e4a7b-2c95-4f18-8b3d-e9a1c7f50d62", "b515b8fc-d13c-4b0b-aae3-33e0ca86ff1c");
    }

    @Test
    void sameLpu_goesByLayer() {
        UUID programId = program(row(alpha).layer(2).begin(JANUARY), row(alpha).layer(1).begin(JULY));

        assertThat(ordered(programId, ProgramLpu::layerNumber)).containsExactly(1, 2);
    }

    @Test
    void sameLayer_goesByBeginDate_rowWithoutBeginFirst() {
        UUID programId = program(row(alpha).begin(JULY), row(alpha).begin(JANUARY), row(alpha).begin(null));

        assertThat(ordered(programId, ProgramLpu::beginDate)).containsExactly(null, JANUARY, JULY);
    }

    @Test
    void sameBegin_goesByAppealType_rowWithoutAppealTypeLast() {
        UUID programId = program(row(alpha).appealType(null), row(alpha).appealType(APPEAL_TYPE_SECOND),
                row(alpha).appealType(APPEAL_TYPE_FIRST));

        assertThat(ordered(programId, ProgramLpu::externalAppealTypeId))
                .containsExactly(APPEAL_TYPE_FIRST, APPEAL_TYPE_SECOND, null);
    }

    @Test
    void sameAppealType_goesByEndDate_openEndLast() {
        UUID programId = program(row(alpha).end(null), row(alpha).end(JULY), row(alpha).end(JANUARY));

        assertThat(ordered(programId, ProgramLpu::endDate)).containsExactly(JANUARY, JULY, null);
    }

    @Test
    void sameEnd_goesByCoverageType() {
        UUID programId = program(row(alpha).coverage(ProgramLpu.CoverageType.R).attached(false),
                row(alpha).coverage(ProgramLpu.CoverageType.D).attached(true));

        assertThat(ordered(programId, ProgramLpu::coverageType))
                .containsExactly(ProgramLpu.CoverageType.D, ProgramLpu.CoverageType.R);
    }

    @Test
    void sameCoverage_goesByDirectAttachment_withoutAttachmentFirst() {
        UUID programId = program(row(alpha).attached(true), row(alpha).attached(false));

        assertThat(ordered(programId, ProgramLpu::directAttachment)).containsExactly(false, true);
    }

    @Test
    void sameAttachment_goesByCopaymentPercent() {
        UUID programId = program(row(alpha).copayment("20.00"), row(alpha).copayment("5.00"));

        assertThat(ordered(programId, row -> row.percentCopayment().toPlainString())).containsExactly("5.00", "20.00");
    }

    @Test
    void otherProgramRows_areNotIncluded() {
        UUID programId = program(row(alpha));
        program(row(alpha).layer(2));

        assertThat(ordered(programId, ProgramLpu::layerNumber)).containsExactly(1);
    }

    private <T> List<@Nullable T> ordered(UUID programId, Function<ProgramLpu, @Nullable T> key) {
        return transactions.execute(status -> programRows.findOrderedByProgramId(programId).stream()
                .map(key)
                .toList());
    }

    private Lpu lpu(String externalId, String name) {
        return lpus.save(Lpu.create(UUID.fromString(externalId),
                new Lpu.Details(name, null, null, null, null, null, false), NOW));
    }

    private UUID program(RowSpec... rows) {
        Program program = Program.create(UUID.randomUUID(),
                new Program.Details(null, null, "Программа РВЛ", null, false), NOW);
        program.replaceAvailableLpu(Arrays.stream(rows).map(RowSpec::toRow).toList(), NOW);
        return programs.save(program).id();
    }

    private static RowSpec row(Lpu lpu) {
        return new RowSpec(lpu, APPEAL_TYPE_FIRST, JANUARY, null, 1, false, new BigDecimal("10.00"),
                ProgramLpu.CoverageType.R);
    }

    private record RowSpec(
            Lpu lpu,
            @Nullable UUID appealTypeId,
            @Nullable LocalDate beginDate,
            @Nullable LocalDate endDate,
            int layerNumber,
            boolean directAttachment,
            BigDecimal copayment,
            ProgramLpu.CoverageType coverageType) {

        RowSpec layer(int value) {
            return new RowSpec(lpu, appealTypeId, beginDate, endDate, value, directAttachment, copayment, coverageType);
        }

        RowSpec begin(@Nullable LocalDate value) {
            return new RowSpec(lpu, appealTypeId, value, endDate, layerNumber, directAttachment, copayment, coverageType);
        }

        RowSpec appealType(@Nullable UUID value) {
            return new RowSpec(lpu, value, beginDate, endDate, layerNumber, directAttachment, copayment, coverageType);
        }

        RowSpec end(@Nullable LocalDate value) {
            return new RowSpec(lpu, appealTypeId, beginDate, value, layerNumber, directAttachment, copayment, coverageType);
        }

        RowSpec coverage(ProgramLpu.CoverageType value) {
            return new RowSpec(lpu, appealTypeId, beginDate, endDate, layerNumber, directAttachment, copayment, value);
        }

        RowSpec attached(boolean value) {
            return new RowSpec(lpu, appealTypeId, beginDate, endDate, layerNumber, value, copayment, coverageType);
        }

        RowSpec copayment(String value) {
            return new RowSpec(lpu, appealTypeId, beginDate, endDate, layerNumber, directAttachment,
                    new BigDecimal(value), coverageType);
        }

        ProgramLpu.Row toRow() {
            return new ProgramLpu.Row(lpu, appealTypeId, beginDate, endDate, layerNumber, directAttachment, copayment,
                    coverageType);
        }
    }
}
