package ru.projectx.datareference.mapper.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.entity.catalog.Lpu;
import ru.projectx.datareference.entity.catalog.ProgramLpu;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.catalog.LpuRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class AvailableLpuReaderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID SANATORIUM = UUID.fromString("31bd3b09-5609-4321-bd8d-43ee2e7ab4ee");
    private static final UUID STRANGER = UUID.fromString("7e6f9e8b-a9f7-4333-813e-9a098ccbe33a");
    private static final UUID APPEAL_TYPE = UUID.fromString("61c5a095-26fd-11dd-b262-001279d33d9f");
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    private final LpuRepository lpuRepository = mock(LpuRepository.class);
    private final AvailableLpuReader reader = new AvailableLpuReader(lpuRepository);
    private final Lpu sanatorium = Lpu.create(
            SANATORIUM, new Lpu.Details("Санаторий «Заря»", null, null, null, null, null, false), NOW);

    @BeforeEach
    void knownSanatorium() {
        when(lpuRepository.findAllByExternalIdIn(anyCollection())).thenReturn(List.of(sanatorium));
    }

    @Test
    void rowWithKnownSanatoriumAndRiskCoverage_isRead() {
        ObjectNode data = program(row(SANATORIUM, "r")
                .put("idTreatment", APPEAL_TYPE.toString())
                .put("beginDate", "2026-01-01T00:00:00+03:00")
                .put("endDate", "2026-12-31T00:00:00+03:00")
                .put("numberLayer", 2)
                .put("directAttachment", true)
                .put("percentCopayment", new BigDecimal("10.5")));

        AvailableLpuReader.AvailableLpu read = reader.read(data);

        assertThat(read.rows()).singleElement().satisfies(row -> {
            assertThat(row.lpu()).isSameAs(sanatorium);
            assertThat(row.externalAppealTypeId()).isEqualTo(APPEAL_TYPE);
            assertThat(row.beginDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(row.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(row.layerNumber()).isEqualTo(2);
            assertThat(row.directAttachment()).isTrue();
            assertThat(row.percentCopayment()).isEqualByComparingTo("10.5");
            assertThat(row.coverageType()).isEqualTo(ProgramLpu.CoverageType.R);
        });
        assertThat(read.received()).isEqualTo(1);
        assertThat(read.dropped()).isZero();
    }

    @Test
    void depositOnlyRow_hasDepositCoverage() {
        AvailableLpuReader.AvailableLpu read = reader.read(program(row(SANATORIUM, "d")));

        assertThat(read.rows()).singleElement()
                .extracting(ProgramLpu.Row::coverageType)
                .isEqualTo(ProgramLpu.CoverageType.D);
    }

    @Test
    void rowWithoutOptionalFields_hasNoAppealTypeAndOpenDates() {
        ObjectNode data = program(row(SANATORIUM, "r")
                .put("idTreatment", "00000000-0000-0000-0000-000000000000")
                .put("beginDate", "0001-01-01T00:00:00+00:00"));

        ProgramLpu.Row row = reader.read(data).rows().getFirst();

        assertThat(row.externalAppealTypeId()).isNull();
        assertThat(row.beginDate()).isNull();
        assertThat(row.endDate()).isNull();
    }

    @Test
    void zeroCopaymentAndIndirectAttachment_areReadAsSent() {
        ObjectNode data = program(row(SANATORIUM, "r")
                .put("numberLayer", 3)
                .put("directAttachment", false)
                .put("percentCopayment", 0));

        ProgramLpu.Row row = reader.read(data).rows().getFirst();

        assertThat(row.layerNumber()).isEqualTo(3);
        assertThat(row.directAttachment()).isFalse();
        assertThat(row.percentCopayment()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"numberLayer", "directAttachment", "percentCopayment"})
    void storedRowWithoutRequiredTerm_rejectsTheMessage(String field) {
        ObjectNode incomplete = row(SANATORIUM, "d");
        incomplete.remove(field);
        ObjectNode data = program(row(SANATORIUM, "r"), incomplete);

        assertThatThrownBy(() -> reader.read(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля " + field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"numberLayer", "directAttachment", "percentCopayment"})
    void droppedRowWithoutRequiredTerm_isOnlyCounted(String field) {
        ObjectNode stranger = row(STRANGER, "r");
        stranger.remove(field);
        ObjectNode withoutCoverage = row(SANATORIUM, null);
        withoutCoverage.remove(field);

        AvailableLpuReader.AvailableLpu read = reader.read(program(row(SANATORIUM, "r"), stranger, withoutCoverage));

        assertThat(read.rows()).hasSize(1);
        assertThat(read.unknownSanatorium()).isEqualTo(1);
        assertThat(read.withoutCoverage()).isEqualTo(1);
    }

    @Test
    void rowWithRiskAndDeposit_isDroppedAsRowWithoutCoverage() {
        ObjectNode both = row(SANATORIUM, "r").put("d", true);

        AvailableLpuReader.AvailableLpu read = reader.read(program(row(SANATORIUM, "d"), both));

        assertThat(read.rows()).singleElement()
                .extracting(ProgramLpu.Row::coverageType)
                .isEqualTo(ProgramLpu.CoverageType.D);
        assertThat(read.withoutCoverage()).isEqualTo(1);
        assertThat(read.unknownSanatorium()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "numberLayer, -1, поле numberLayer отрицательное",
            "percentCopayment, -0.01, поле percentCopayment вне диапазона от 0 до 100",
            "percentCopayment, 100.01, поле percentCopayment вне диапазона от 0 до 100"})
    void storedRowWithTermOutOfRange_rejectsTheMessage(String field, String value, String reason) {
        ObjectNode outOfRange = row(SANATORIUM, "d");
        outOfRange.set(field, JSON.readTree(value));
        ObjectNode data = program(row(SANATORIUM, "r"), outOfRange);

        assertThatThrownBy(() -> reader.read(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage(reason);
    }

    @Test
    void zeroLayerAndFullCopayment_areReadAsSent() {
        ObjectNode data = program(row(SANATORIUM, "r").put("numberLayer", 0).put("percentCopayment", 100));

        ProgramLpu.Row row = reader.read(data).rows().getFirst();

        assertThat(row.layerNumber()).isZero();
        assertThat(row.percentCopayment()).isEqualByComparingTo("100");
    }

    @Test
    void droppedRowWithTermOutOfRange_isOnlyCounted() {
        ObjectNode stranger = row(STRANGER, "r").put("numberLayer", -1);
        ObjectNode both = row(SANATORIUM, "r").put("d", true).put("percentCopayment", 101);

        AvailableLpuReader.AvailableLpu read = reader.read(program(row(SANATORIUM, "r"), stranger, both));

        assertThat(read.rows()).hasSize(1);
        assertThat(read.unknownSanatorium()).isEqualTo(1);
        assertThat(read.withoutCoverage()).isEqualTo(1);
    }

    @Test
    void droppedRows_areCountedByReasonSeparately() {
        ObjectNode data = program(
                row(SANATORIUM, "r"),
                row(STRANGER, "r"),
                row(SANATORIUM, null),
                row(SANATORIUM, "d"));

        AvailableLpuReader.AvailableLpu read = reader.read(data);

        assertThat(read.rows()).hasSize(2);
        assertThat(read.received()).isEqualTo(4);
        assertThat(read.dropped()).isEqualTo(2);
        assertThat(read.unknownSanatorium()).isEqualTo(1);
        assertThat(read.withoutCoverage()).isEqualTo(1);
    }

    @Test
    void rowWithoutSanatoriumReference_countsAsUnknownSanatorium() {
        ObjectNode data = program(row(SANATORIUM, "r"));
        ((ObjectNode) data.path("service").get(0)).remove("idLPU");

        AvailableLpuReader.AvailableLpu read = reader.read(data);

        assertThat(read.rows()).isEmpty();
        assertThat(read.unknownSanatorium()).isEqualTo(1);
        assertThat(read.withoutCoverage()).isZero();
    }

    @Test
    void sanatoriumsOfTheWholeList_areLoadedByOneQuery() {
        reader.read(program(row(SANATORIUM, "r"), row(STRANGER, "d"), row(SANATORIUM, "d")));

        verify(lpuRepository).findAllByExternalIdIn(Set.of(SANATORIUM, STRANGER));
    }

    @Test
    void programWithoutList_hasNoRows() {
        AvailableLpuReader.AvailableLpu read = reader.read(JSON.createObjectNode());

        assertThat(read.rows()).isEmpty();
        assertThat(read.received()).isZero();
        assertThat(read.dropped()).isZero();
    }

    private static ObjectNode program(ObjectNode... rows) {
        ObjectNode data = JSON.createObjectNode();
        ArrayNode service = data.putArray("service");
        for (ObjectNode row : rows) {
            service.add(row);
        }
        return data;
    }

    private static ObjectNode row(UUID sanatorium, String coverage) {
        ObjectNode row = JSON.createObjectNode()
                .put("idLPU", sanatorium.toString())
                .put("numberLayer", 1)
                .put("directAttachment", true)
                .put("percentCopayment", new BigDecimal("10.00"));
        if (coverage != null) {
            row.put(coverage, true);
        }
        return row;
    }
}
