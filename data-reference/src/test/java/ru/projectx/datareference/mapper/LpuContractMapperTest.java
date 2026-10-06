package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.ForeignReferenceRepository;
import ru.projectx.datareference.repository.LpuContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает разбор договоров санаториев. Договор принимается, только если он принадлежит санаторию из
 * справочника, причём договор отделения достаётся отделению, а не головной организации. Отделение, которого ещё нет,
 * договор ждёт; головная организация решает, только когда отделения нет или оно признано не нашим. Договор не
 * нашего контрагента пропускается и запоминается как не наш, чтобы его услуги не ждали его. Пока имя признака
 * договора на восстановительное лечение не задано, этот фильтр не применяется. Известный договор получает реквизиты
 * последнего сообщения и может перейти к другому санаторию. Данные вымышленные.
 */
class LpuContractMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ZERO_GUID = "00000000-0000-0000-0000-000000000000";
    private static final String RVL_PROPERTY = "Договор на РВЛ";
    private static final String SOB_PROPERTY = "Выгружать прейскуранты в систему бронирования";
    private static final UUID CONTRACT = UUID.fromString("2abf08e6-0000-4000-8000-00000000000a");
    private static final UUID HEAD_OFFICE = UUID.fromString("62dba5f6-0000-4000-8000-000000000001");
    private static final UUID BRANCH = UUID.fromString("62dba5f6-0000-4000-8000-000000000002");
    private static final UUID MAIN_CONTRACT = UUID.fromString("2abf08e6-0000-4000-8000-0000000000aa");
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);

    private final LpuContractRepository repository = mock(LpuContractRepository.class);
    private final LpuRepository lpuRepository = mock(LpuRepository.class);
    private final ForeignReferenceRepository foreignRepository = mock(ForeignReferenceRepository.class);
    private final Lpu headOffice = mock(Lpu.class);
    private final Lpu branch = mock(Lpu.class);

    @Test
    void streamIsLpuContracts() {
        assertThat(mapper(null).stream()).isEqualTo(OneCStream.LPU_CONTRACTS);
    }

    @Test
    void newContractOfOurSanatorium_isCreatedFromTheMessage() {
        knownSanatoriums();
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        data.put("mainContractId", MAIN_CONTRACT.toString());
        data.put("usePricesMainAgreement", true);
        data.set("extProperties", properties(property(SOB_PROPERTY, true)));

        ImportResult result = mapper(null).apply(data);

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        LpuContract saved = saved();
        assertThat(saved.externalId()).isEqualTo(CONTRACT);
        assertThat(saved.lpu()).isSameAs(headOffice);
        assertThat(saved.number()).isEqualTo("Д-0001");
        assertThat(saved.name()).isEqualTo("Договор на санаторное лечение");
        assertThat(saved.mainContractExternalId()).isEqualTo(MAIN_CONTRACT);
        assertThat(saved.usePricesMainContract()).isTrue();
        assertThat(saved.uploadedToSob()).isTrue();
        assertThat(saved.beginDate()).isEqualTo(LocalDate.of(2024, 10, 10));
        assertThat(saved.endDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(saved.isArchive()).isFalse();
        assertThat(saved.createdAt()).isEqualTo(NOW);
    }

    @Test
    void emptyOptionalFields_areStoredAsEmpty() {
        knownSanatoriums();
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        data.remove("number");
        data.put("name", "  ");
        data.put("endDate", "3000-01-01T00:00:00+03:00");

        mapper(null).apply(data);

        LpuContract saved = saved();
        assertThat(saved.number()).isNull();
        assertThat(saved.name()).isNull();
        assertThat(saved.mainContractExternalId()).isNull();
        assertThat(saved.endDate()).isNull();
        assertThat(saved.usePricesMainContract()).isFalse();
    }

    @Test
    void contractWithoutSobProperty_isNotUploadedToSob() {
        knownSanatoriums();
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        data.set("extProperties", properties(property("Другое свойство", true), property(SOB_PROPERTY, false)));

        mapper(null).apply(data);

        assertThat(saved().uploadedToSob()).isFalse();
    }

    @Test
    void contractOfBranch_belongsToBranchNotHeadOffice() {
        knownSanatoriums();

        mapper(null).apply(contract(HEAD_OFFICE, BRANCH));

        assertThat(saved().lpu()).isSameAs(branch);
    }

    @Test
    void contractOfForeignBranch_belongsToHeadOffice() {
        when(lpuRepository.findByExternalId(HEAD_OFFICE)).thenReturn(Optional.of(headOffice));
        foreign(BRANCH);

        ImportResult result = mapper(null).apply(contract(HEAD_OFFICE, BRANCH));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(saved().lpu()).isSameAs(headOffice);
        verify(foreignRepository, never()).remember(any(), anyString(), any());
    }

    @Test
    void contractOfBranchNotYetReceived_waitsForTheBranch_evenWhenTheHeadOfficeIsKnown() {
        when(lpuRepository.findByExternalId(HEAD_OFFICE)).thenReturn(Optional.of(headOffice));

        ImportResult result = mapper(null).apply(contract(HEAD_OFFICE, BRANCH));

        assertThat(result).isEqualTo(awaiting(BRANCH));
        verify(repository, never()).save(any());
        verify(foreignRepository, never()).remember(any(), anyString(), any());
    }

    @Test
    void contractOfForeignBranch_waitsForTheHeadOffice_notYetReceived() {
        foreign(BRANCH);

        ImportResult result = mapper(null).apply(contract(HEAD_OFFICE, BRANCH));

        assertThat(result).isEqualTo(awaiting(HEAD_OFFICE));
        verify(repository, never()).save(any());
    }

    @Test
    void contractOfBranchWithoutKnownHeadOffice_belongsToBranch() {
        when(lpuRepository.findByExternalId(BRANCH)).thenReturn(Optional.of(branch));

        mapper(null).apply(contract(HEAD_OFFICE, BRANCH));

        assertThat(saved().lpu()).isSameAs(branch);
    }

    @Test
    void contractWithoutBranch_belongsToHeadOffice() {
        knownSanatoriums();
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        data.put("contragentBranchId", ZERO_GUID);

        mapper(null).apply(data);

        assertThat(saved().lpu()).isSameAs(headOffice);
    }

    @Test
    void contractWithoutBranch_waitsForTheHeadOffice_notYetReceived() {
        ImportResult result = mapper(null).apply(contract(HEAD_OFFICE, HEAD_OFFICE));

        assertThat(result).isEqualTo(awaiting(HEAD_OFFICE));
        verify(repository, never()).save(any());
    }

    @Test
    void contractOfForeignContragents_isSkippedNamingBothReferences_andRememberedAsForeign() {
        foreign(HEAD_OFFICE);
        foreign(BRANCH);

        ImportResult result = mapper(null).apply(contract(HEAD_OFFICE, BRANCH));

        assertThat(result).isEqualTo(ImportResult.skipped(
                "договор не нашего контрагента: ownerId=" + HEAD_OFFICE + ", contragentBranchId=" + BRANCH));
        verify(repository, never()).save(any());
        verify(foreignRepository).remember(CONTRACT, "LPU_CONTRACT", NOW);
    }

    @Test
    void contractWithoutAnyReference_isSkippedNamingEmptyReferences() {
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        data.remove("ownerId");
        data.put("contragentBranchId", ZERO_GUID);

        ImportResult result = mapper(null).apply(data);

        assertThat(result).isEqualTo(
                ImportResult.skipped("договор не нашего контрагента: ownerId=null, contragentBranchId=null"));
        verifyNoInteractions(lpuRepository, repository);
    }

    @Test
    void recordOfTheMessage_isTheContract() {
        assertThat(mapper(null).recordOf(contract(HEAD_OFFICE, BRANCH))).contains(CONTRACT);
    }

    @Test
    void contractWithRvlPropertySet_isApplied() {
        knownSanatoriums();
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        data.set("extProperties", properties(property(SOB_PROPERTY, false), property(RVL_PROPERTY, true)));

        ImportResult result = mapper(RVL_PROPERTY).apply(data);

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(saved().uploadedToSob()).isFalse();
    }

    @Test
    void contractWithRvlPropertyCleared_isSkippedNamingTheProperty() {
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        data.set("extProperties", properties(property(RVL_PROPERTY, false), property(SOB_PROPERTY, true)));

        ImportResult result = mapper(RVL_PROPERTY).apply(data);

        assertThat(result).isEqualTo(ImportResult.skipped("договор без признака " + RVL_PROPERTY));
        verifyNoInteractions(lpuRepository, repository);
        verify(foreignRepository).remember(CONTRACT, "LPU_CONTRACT", NOW);
    }

    @Test
    void contractWithoutRvlProperty_isSkipped_evenWhenAnotherPropertyIsSet() {
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        data.set("extProperties", properties(property(SOB_PROPERTY, true)));

        ImportResult result = mapper(RVL_PROPERTY).apply(data);

        assertThat(result).isEqualTo(ImportResult.skipped("договор без признака " + RVL_PROPERTY));
    }

    @Test
    void rvlPropertyWithoutValue_doesNotPassTheFilter() {
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        ObjectNode bare = JSON.createObjectNode();
        bare.put("propertyName", RVL_PROPERTY);
        data.set("extProperties", properties(bare));

        ImportResult result = mapper(RVL_PROPERTY).apply(data);

        assertThat(result).isInstanceOf(ImportResult.Skipped.class);
    }

    @Test
    void blankRvlPropertySetting_appliesNoFilter() {
        knownSanatoriums();

        ImportResult result = mapper(" ").apply(contract(HEAD_OFFICE, HEAD_OFFICE));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
    }

    @Test
    void knownContract_takesTheLastSnapshotAndTheSanatorium() {
        knownSanatoriums();
        var oldDetails = new LpuContract.Details("Д-0000", null, null, false, true,
                LocalDate.of(2020, 1, 1), null, false);
        LpuContract known = LpuContract.create(CONTRACT, headOffice, oldDetails, EARLIER);
        when(repository.findByExternalId(CONTRACT)).thenReturn(Optional.of(known));
        ObjectNode data = contract(HEAD_OFFICE, BRANCH);
        data.put("deleteMark", true);

        ImportResult result = mapper(null).apply(data);

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(saved()).isSameAs(known);
        assertThat(known.lpu()).isSameAs(branch);
        assertThat(known.number()).isEqualTo("Д-0001");
        assertThat(known.uploadedToSob()).isFalse();
        assertThat(known.beginDate()).isEqualTo(LocalDate.of(2024, 10, 10));
        assertThat(known.isArchive()).isTrue();
        assertThat(known.createdAt()).isEqualTo(EARLIER);
        assertThat(known.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void contractWithoutBeginDate_isContractViolation_andNothingIsSaved() {
        knownSanatoriums();
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        data.put("beginDate", "0001-01-01T00:00:00+03:00");

        assertThatThrownBy(() -> mapper(null).apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("у договора ЛПУ нет даты начала действия");
        verify(repository, never()).save(any());
    }

    @Test
    void missingBeginDate_isReportedBeforeAMalformedMainContract() {
        knownSanatoriums();
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        data.remove("beginDate");
        data.put("mainContractId", "1-2-3-4-5");

        assertThatThrownBy(() -> mapper(null).apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("у договора ЛПУ нет даты начала действия");
    }

    @Test
    void contractWithoutGuid_isContractViolation_andNothingIsSaved() {
        knownSanatoriums();
        ObjectNode data = contract(HEAD_OFFICE, HEAD_OFFICE);
        data.remove("id");

        assertThatThrownBy(() -> mapper(null).apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" id");
        verify(repository, never()).save(any());
    }

    private LpuContractMapper mapper(String rvlProperty) {
        var properties = new OneCContractProperties(null, rvlProperty, null, "Адрес", List.of());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new LpuContractMapper(
                repository, lpuRepository, new ForeignReferences(foreignRepository, clock), properties, clock);
    }

    private void foreign(UUID contragent) {
        when(foreignRepository.existsByExternalIdAndKind(contragent, ParentKind.CONTRAGENT)).thenReturn(true);
    }

    private static ImportResult awaiting(UUID contragent) {
        return ImportResult.awaitingParent(new AwaitedParent(ParentKind.CONTRAGENT, contragent));
    }

    private void knownSanatoriums() {
        when(lpuRepository.findByExternalId(HEAD_OFFICE)).thenReturn(Optional.of(headOffice));
        when(lpuRepository.findByExternalId(BRANCH)).thenReturn(Optional.of(branch));
    }

    private LpuContract saved() {
        ArgumentCaptor<LpuContract> saved = ArgumentCaptor.forClass(LpuContract.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    /** Договор в том виде, в каком его присылает 1С, сокращённый до полей, которые читает маппер. */
    private static ObjectNode contract(UUID ownerId, UUID branchId) {
        ObjectNode data = JSON.createObjectNode();
        data.put("id", CONTRACT.toString());
        data.put("ownerId", ownerId.toString());
        data.put("contragentBranchId", branchId.toString());
        data.put("mainContractId", ZERO_GUID);
        data.put("usePricesMainAgreement", false);
        data.put("number", " Д-0001");
        data.put("name", "Договор на санаторное лечение");
        data.put("beginDate", "2024-10-10T00:00:00+03:00");
        data.put("endDate", "2026-10-31T00:00:00+03:00");
        data.put("deleteMark", false);
        return data;
    }

    private static ArrayNode properties(ObjectNode... rows) {
        ArrayNode properties = JSON.createArrayNode();
        List.of(rows).forEach(properties::add);
        return properties;
    }

    private static ObjectNode property(String name, boolean value) {
        ObjectNode row = JSON.createObjectNode();
        row.put("propertyName", name);
        row.putObject("value").put("type", "boolean").put("value", value);
        return row;
    }
}
