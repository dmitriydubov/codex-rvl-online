package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.Insurer;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InsurerRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.service.onec.ImportResult;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает разбор потока контрагентов: санаторий принимается только с видом деятельности «РВЛ»,
 * санаторий, потерявший этот вид, становится архивным, а страхователь сохраняется в свой справочник.
 * Отдельно защищено чтение настройки GUID вида деятельности «РВЛ»: запись не в канонической форме
 * останавливает старт сервиса, потому что стандартное чтение UUID превратило бы её в чужой GUID,
 * и все санатории молча ушли бы в пропуск.
 */
class ContragentMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RVL = "f3876c53-c7d8-4620-b364-9538164ba5d4";
    private static final String OTHER_ACTIVITY = "0b6b9b1e-4f4b-4f4a-9a43-1d1f5d1a2b3c";
    private static final UUID CONTRAGENT = UUID.fromString("7c0e4d4a-2f39-4c55-8b4e-5f1d3b9d6e21");
    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    private final LpuRepository lpuRepository = mock(LpuRepository.class);
    private final InsurerRepository insurerRepository = mock(InsurerRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @ParameterizedTest
    @ValueSource(strings = {"1-2-3-4-5", "0-0-0-0-0", "f3876c53c7d8-4620-b364-9538164ba5d4-", " " + RVL})
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
    }

    @Test
    void newSanatoriumWithRvl_isSaved() {
        when(lpuRepository.findByExternalId(CONTRAGENT)).thenReturn(Optional.empty());

        ImportResult result = mapper(RVL).apply(sanatorium(OTHER_ACTIVITY, RVL));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        Lpu saved = savedLpu();
        assertThat(saved.name()).isEqualTo("Санаторий «Сосны»");
        assertThat(saved.inn()).isEqualTo("7700000000");
        assertThat(saved.isArchive()).isFalse();
    }

    @Test
    void newSanatoriumWithoutRvl_isSkipped_andNotSaved() {
        when(lpuRepository.findByExternalId(CONTRAGENT)).thenReturn(Optional.empty());

        ImportResult result = mapper(RVL).apply(sanatorium(OTHER_ACTIVITY));

        assertThat(result).isInstanceOfSatisfying(ImportResult.Skipped.class,
                skipped -> assertThat(skipped.reason()).contains("РВЛ"));
        verify(lpuRepository, never()).save(any());
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
        ArgumentCaptor<Insurer> saved = ArgumentCaptor.forClass(Insurer.class);
        verify(insurerRepository).save(saved.capture());
        assertThat(saved.getValue().name()).isEqualTo("АО «Страховщик»");
        verify(lpuRepository, never()).save(any());
    }

    private ContragentMapper mapper(String rvlActivity) {
        var contract = new OneCContractProperties(rvlActivity, null, null, "Адрес", List.of());
        return new ContragentMapper(lpuRepository, insurerRepository, contract, clock);
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
