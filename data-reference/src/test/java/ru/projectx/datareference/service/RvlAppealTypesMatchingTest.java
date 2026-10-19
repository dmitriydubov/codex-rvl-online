package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static ru.projectx.datareference.support.TestApiProperties.RVL_AND_SKL;
import static ru.projectx.datareference.support.TestApiProperties.RVL_IN_RUSSIA;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import ru.projectx.datareference.repository.AppealTypeRepository;
import ru.projectx.datareference.service.rest.ReadFixtures;
import ru.projectx.datareference.support.TestApiProperties;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RvlAppealTypesMatchingTest {

    private static final UUID SKL = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID RUSSIA = UUID.fromString("a0000000-0000-4000-8000-000000000002");
    private static final Set<String> NORMALIZED = Set.of("рвл и скл", "рвл в россии");

    @Mock
    private AppealTypeRepository appealTypes;

    private RvlAppealTypes rvlAppealTypes;

    @BeforeEach
    void setUp() {
        rvlAppealTypes = new RvlAppealTypes(appealTypes, TestApiProperties.defaults());
    }

    @Test
    void byExternalId_mapsEveryFoundTypeByItsGuid_lookedUpByNormalizedNames() {
        when(appealTypes.findActiveByNormalizedNameIn(NORMALIZED)).thenReturn(List.of(
                ReadFixtures.appealType(SKL, "  рвл и СКЛ "), ReadFixtures.appealType(RUSSIA, RVL_IN_RUSSIA)));

        assertThat(rvlAppealTypes.byExternalId())
                .containsOnlyKeys(SKL, RUSSIA)
                .allSatisfy((id, type) -> assertThat(type.externalId()).isEqualTo(id));
        assertThat(rvlAppealTypes.externalIds()).containsExactlyInAnyOrder(SKL, RUSSIA);
    }

    @Test
    void missing_namesConfiguredNamesAbsentInReference_matchingWithoutCaseAndEdgeSpaces() {
        when(appealTypes.findActiveByNormalizedNameIn(NORMALIZED))
                .thenReturn(List.of(ReadFixtures.appealType(SKL, "  рвл и СКЛ ")));

        assertThat(rvlAppealTypes.missing()).containsExactly(RVL_IN_RUSSIA);
    }

    @Test
    void missing_isEmptyWhenEveryNameIsFound() {
        when(appealTypes.findActiveByNormalizedNameIn(NORMALIZED)).thenReturn(List.of(
                ReadFixtures.appealType(SKL, RVL_AND_SKL), ReadFixtures.appealType(RUSSIA, RVL_IN_RUSSIA)));

        assertThat(rvlAppealTypes.missing()).isEmpty();
        assertThat(rvlAppealTypes.externalIds()).hasSize(2);
    }

    @Test
    void warnIfMissing_writesWarnNamingMissingNames(CapturedOutput output) {
        when(appealTypes.findActiveByNormalizedNameIn(NORMALIZED)).thenReturn(List.of());

        rvlAppealTypes.warnIfMissing();

        assertThat(output).contains("WARN", "не найдены в appeal_types", RVL_AND_SKL, RVL_IN_RUSSIA);
    }

    @Test
    void warnIfMissing_isSilentWhenEveryNameIsFound(CapturedOutput output) {
        when(appealTypes.findActiveByNormalizedNameIn(NORMALIZED)).thenReturn(List.of(
                ReadFixtures.appealType(SKL, RVL_AND_SKL), ReadFixtures.appealType(RUSSIA, RVL_IN_RUSSIA)));

        rvlAppealTypes.warnIfMissing();

        assertThat(output).doesNotContain("не найдены в appeal_types");
    }
}
