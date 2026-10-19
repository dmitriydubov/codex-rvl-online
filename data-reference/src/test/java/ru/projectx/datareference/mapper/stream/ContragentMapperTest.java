package ru.projectx.datareference.mapper.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.catalog.Insurer;
import ru.projectx.datareference.entity.catalog.Lpu;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.ForeignReferences;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.repository.ForeignReferenceRepository;
import ru.projectx.datareference.repository.InsurerRepository;
import ru.projectx.datareference.repository.LpuRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ContragentMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RVL = "f3876c53-c7d8-4620-b364-9538164ba5d4";
    private static final String OTHER_ACTIVITY = "0b6b9b1e-4f4b-4f4a-9a43-1d1f5d1a2b3c";
    private static final UUID CONTRAGENT = UUID.fromString("7c0e4d4a-2f39-4c55-8b4e-5f1d3b9d6e21");
    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    private final LpuRepository lpuRepository = mock(LpuRepository.class);
    private final InsurerRepository insurerRepository = mock(InsurerRepository.class);
    private final ForeignReferenceRepository foreignRepository = mock(ForeignReferenceRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @ParameterizedTest
    @ValueSource(strings = {
            "1-2-3-4-5",
            "0-0-0-0-0",
            "00000000-0000-0000-0000-000000000000",
            "f3876c53c7d8-4620-b364-9538164ba5d4-",
            " " + RVL})
    void nonCanonicalRvlActivitySetting_stopsTheStart_withoutRepeatingTheValue(String setting) {
        assertThatIllegalStateException()
                .isThrownBy(() -> mapper(setting))
                .withMessageContaining("projectx.onec.contract.rvl-activity-id")
                .withMessageContaining("8-4-4-4-12")
                .withMessageNotContaining(setting.strip());
    }

    @Test
    void canonicalRvlActivitySettingInUpperCase_isTheSameActivity() {
        when(lpuRepository.findByExternalId(CONTRAGENT)).thenReturn(Optional.empty());

        ImportResult result = mapper(RVL.toUpperCase()).apply(sanatorium(RVL));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
    }

    @Test
    void streamIsContragents() {
        assertThat(mapper(RVL).stream()).isEqualTo(OneCStream.CONTRAGENTS);
    }

    @Test
    void sanatorium_withoutRvlActivitySetting_isSkippedNamingTheSetting() {
        ImportResult result = mapper(null).apply(sanatorium(RVL));

        assertThat(result).isInstanceOfSatisfying(ImportResult.Skipped.class,
                skipped -> assertThat(skipped.reason()).contains("rvl-activity-id"));
        verify(lpuRepository, never()).save(any());
        verify(foreignRepository, never()).remember(any(), anyString(), any());
    }

    @Test
    void newSanatoriumWithRvl_isSaved() {
        when(lpuRepository.findByExternalId(CONTRAGENT)).thenReturn(Optional.empty());

        ImportResult result = mapper(RVL).apply(sanatorium(OTHER_ACTIVITY, RVL));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        verify(foreignRepository, never()).remember(any(), anyString(), any());
        Lpu saved = savedLpu();
        assertThat(saved.name()).isEqualTo("Санаторий «Сосны»");
        assertThat(saved.inn()).isEqualTo("7700000000");
        assertThat(saved.isArchive()).isFalse();
    }

    @Test
    void newSanatoriumWithoutRvl_isSkipped_notSaved_andRememberedAsForeign() {
        when(lpuRepository.findByExternalId(CONTRAGENT)).thenReturn(Optional.empty());

        ImportResult result = mapper(RVL).apply(sanatorium(OTHER_ACTIVITY));

        assertThat(result).isEqualTo(ImportResult.skipped("контрагент без вида деятельности РВЛ"));
        verify(lpuRepository, never()).save(any());
        verify(foreignRepository).remember(CONTRAGENT, "CONTRAGENT", NOW);
    }

    @Test
    void knownSanatoriumWithRvl_getsTheNewDetails() {
        Lpu known = knownSanatorium();
        when(lpuRepository.findByExternalId(CONTRAGENT)).thenReturn(Optional.of(known));

        ImportResult result = mapper(RVL).apply(sanatorium(RVL));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(savedLpu()).isSameAs(known);
        assertThat(known.name()).isEqualTo("Санаторий «Сосны»");
        assertThat(known.isArchive()).isFalse();
    }

    @Test
    void knownSanatoriumThatLostRvl_isArchived_withTheNewDetails() {
        Lpu known = knownSanatorium();
        when(lpuRepository.findByExternalId(CONTRAGENT)).thenReturn(Optional.of(known));

        ImportResult result = mapper(RVL).apply(sanatorium(OTHER_ACTIVITY));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        verify(foreignRepository, never()).remember(any(), anyString(), any());
        assertThat(savedLpu()).isSameAs(known);
        assertThat(known.name()).isEqualTo("Санаторий «Сосны»");
        assertThat(known.isArchive()).isTrue();
    }

    @Test
    void insurer_isSaved_andTheOutcomeNamesWhereItWent() {
        when(insurerRepository.findByExternalId(CONTRAGENT)).thenReturn(Optional.empty());

        ImportResult result = mapper(RVL).apply(insurer());

        assertThat(result).isInstanceOfSatisfying(ImportResult.Skipped.class,
                skipped -> assertThat(skipped.reason()).contains("страхователей"));
        verify(foreignRepository).remember(CONTRAGENT, "CONTRAGENT", NOW);
        ArgumentCaptor<Insurer> saved = ArgumentCaptor.forClass(Insurer.class);
        verify(insurerRepository).save(saved.capture());
        assertThat(saved.getValue().name()).isEqualTo("АО «Страховщик»");
        verify(lpuRepository, never()).save(any());
    }

    @Test
    void knownInsurer_getsTheNewDetails() {
        var oldDetails = new Insurer.Details("Старое название", null, null, null, false);
        Insurer known = Insurer.create(CONTRAGENT, oldDetails, NOW.minusSeconds(3600));
        when(insurerRepository.findByExternalId(CONTRAGENT)).thenReturn(Optional.of(known));

        ImportResult result = mapper(RVL).apply(insurer());

        assertThat(result).isInstanceOf(ImportResult.Skipped.class);
        ArgumentCaptor<Insurer> saved = ArgumentCaptor.forClass(Insurer.class);
        verify(insurerRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(known);
        assertThat(known.name()).isEqualTo("АО «Страховщик»");
        assertThat(known.inn()).isEqualTo("7700000000");
    }

    @ParameterizedTest
    @CsvSource({"true, ИНН, 12", "true, КПП, 9", "false, ИНН, 12", "false, КПП, 9"})
    void requisiteLongerThanItsColumn_isContractViolation(boolean sanatorium, String field, int limit) {
        ObjectNode data = sanatorium ? sanatorium(RVL) : insurer();
        data.put(field, "7".repeat(limit + 1));

        assertThatThrownBy(() -> mapper(RVL).apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле %s длиннее %d символов".formatted(field, limit));
        verify(lpuRepository, never()).save(any());
        verify(insurerRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"ИНН, 12", "КПП, 9"})
    void sanatoriumRequisiteAtItsLimitWithSpaces_isSavedTrimmed(String field, int limit) {
        ObjectNode data = sanatorium(RVL);
        data.put(field, " " + "7".repeat(limit) + " ");

        mapper(RVL).apply(data);

        Lpu saved = savedLpu();
        assertThat("ИНН".equals(field) ? saved.inn() : saved.kpp()).isEqualTo("7".repeat(limit));
    }

    @ParameterizedTest
    @CsvSource({"ИНН, 12", "КПП, 9"})
    void insurerRequisiteAtItsLimitWithSpaces_isSavedTrimmed(String field, int limit) {
        ObjectNode data = insurer();
        data.put(field, " " + "7".repeat(limit) + " ");

        mapper(RVL).apply(data);

        ArgumentCaptor<Insurer> saved = ArgumentCaptor.forClass(Insurer.class);
        verify(insurerRepository).save(saved.capture());
        Insurer insurer = saved.getValue();
        assertThat("ИНН".equals(field) ? insurer.inn() : insurer.kpp()).isEqualTo("7".repeat(limit));
    }

    @Test
    void sanatoriumWithoutOptionalDetails_storesThemAsAbsent() {
        when(lpuRepository.findByExternalId(CONTRAGENT)).thenReturn(Optional.empty());

        mapper(RVL).apply(sanatorium(RVL));

        Lpu saved = savedLpu();
        assertThat(saved.fullName()).isNull();
        assertThat(saved.kpp()).isNull();
        assertThat(saved.phone()).isNull();
        assertThat(saved.email()).isNull();
    }

    @Test
    void insurerWithoutOptionalDetails_storesThemAsAbsent() {
        mapper(RVL).apply(insurer());

        ArgumentCaptor<Insurer> saved = ArgumentCaptor.forClass(Insurer.class);
        verify(insurerRepository).save(saved.capture());
        assertThat(saved.getValue().fullName()).isNull();
        assertThat(saved.getValue().kpp()).isNull();
    }

    private ContragentMapper mapper(String rvlActivity) {
        var contract = new OneCContractProperties(rvlActivity, null, List.of(), "Страхователь", "Адрес", List.of());
        return new ContragentMapper(
                lpuRepository, insurerRepository, new ForeignReferences(foreignRepository, clock), contract, clock);
    }

    private Lpu savedLpu() {
        ArgumentCaptor<Lpu> saved = ArgumentCaptor.forClass(Lpu.class);
        verify(lpuRepository).save(saved.capture());
        return saved.getValue();
    }

    private static Lpu knownSanatorium() {
        var details = new Lpu.Details("Старое название", null, null, null, null, null, false);
        return Lpu.create(CONTRAGENT, details, NOW.minusSeconds(3600));
    }

    private static ObjectNode sanatorium(String... activities) {
        ObjectNode data = contragent(true, "Санаторий «Сосны»");
        var rows = data.putArray("typesActivities");
        for (String activity : activities) {
            rows.addObject().put("guidTypeActivities", activity);
        }
        return data;
    }

    private static ObjectNode insurer() {
        return contragent(false, "АО «Страховщик»");
    }

    private static ObjectNode contragent(boolean sanatorium, String name) {
        ObjectNode data = JSON.createObjectNode();
        data.put("guid", CONTRAGENT.toString());
        data.put("thisIsLPU", sanatorium);
        data.put("Наименование", name);
        data.put("ИНН", "7700000000");
        data.put("deleteMark", false);
        return data;
    }
}
