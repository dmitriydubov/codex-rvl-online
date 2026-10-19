package ru.projectx.datareference.mapper.stream;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import ru.projectx.datareference.entity.inbound.AwaitedParent;
import ru.projectx.datareference.entity.catalog.ContragentService;
import ru.projectx.datareference.entity.catalog.LpuContract;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.entity.inbound.ParentKind;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.CatalogReferences;
import ru.projectx.datareference.mapper.ForeignReferences;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.repository.catalog.ContragentServiceRepository;
import ru.projectx.datareference.repository.ForeignReferenceRepository;
import ru.projectx.datareference.repository.catalog.LpuContractRepository;
import ru.projectx.datareference.repository.catalog.LpuRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ContragentServiceMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID SERVICE = UUID.fromString("01a241e0-9497-4a79-8000-8723e71fd24d");
    private static final UUID CONTRACT = UUID.fromString("b39673dd-12ea-11e8-8218-5cb90192efd5");
    private static final UUID APPEAL_TYPE = UUID.fromString("8938e3d2-42f1-11db-a758-000802ce8208");
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);

    private final ContragentServiceRepository repository = mock(ContragentServiceRepository.class);
    private final LpuContractRepository contractRepository = mock(LpuContractRepository.class);
    private final ForeignReferenceRepository foreignRepository = mock(ForeignReferenceRepository.class);
    private final LpuContract contract = mock(LpuContract.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ContragentServiceMapper mapper = new ContragentServiceMapper(repository,
            new CatalogReferences(mock(LpuRepository.class), contractRepository, repository,
                    new ForeignReferences(foreignRepository, clock)), clock);

    @Test
    void streamIsContragentServices() {
        assertThat(mapper.stream()).isEqualTo(OneCStream.CONTRAGENT_SERVICES);
    }

    @Test
    void serviceUnderForeignContract_isSkippedNamingTheContract_andRememberedAsForeign() {
        when(contractRepository.findByExternalId(CONTRACT)).thenReturn(Optional.empty());
        when(foreignRepository.existsByExternalIdAndKind(CONTRACT, ParentKind.LPU_CONTRACT)).thenReturn(true);

        ImportResult result = mapper.apply(service());

        assertThat(result).isEqualTo(ImportResult.skipped("услуга по договору вне справочника: " + CONTRACT));
        verify(foreignRepository).remember(SERVICE, "CONTRAGENT_SERVICE", NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void serviceUnderUnknownContract_waitsForTheContract_andIsNotRememberedAsForeign() {
        when(contractRepository.findByExternalId(CONTRACT)).thenReturn(Optional.empty());

        ImportResult result = mapper.apply(service());

        assertThat(result).isEqualTo(
                ImportResult.awaitingParent(new AwaitedParent(ParentKind.LPU_CONTRACT, CONTRACT)));
        verify(foreignRepository, never()).remember(any(), anyString(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void recordOfTheMessage_isTheService() {
        assertThat(mapper.recordOf(service())).contains(SERVICE);
    }

    @Test
    void newServiceUnderKnownContract_isCreatedFromTheMessage() {
        when(contractRepository.findByExternalId(CONTRACT)).thenReturn(Optional.of(contract));
        when(repository.findByExternalId(SERVICE)).thenReturn(Optional.empty());

        ImportResult result = mapper.apply(service());

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        verify(foreignRepository, never()).remember(any(), anyString(), any());
        ContragentService saved = saved();
        assertThat(saved.externalId()).isEqualTo(SERVICE);
        assertThat(saved.contract()).isSameAs(contract);
        assertThat(saved.externalAppealTypeId()).isEqualTo(APPEAL_TYPE);
        assertThat(saved.code()).isEqualTo("41.178.178");
        assertThat(saved.name()).isEqualTo("Лечебный массаж");
        assertThat(saved.isArchive()).isFalse();
        assertThat(saved.createdAt()).isEqualTo(NOW);
    }

    @Test
    void emptyOptionalFields_areStoredAsEmpty() {
        when(contractRepository.findByExternalId(CONTRACT)).thenReturn(Optional.of(contract));
        when(repository.findByExternalId(SERVICE)).thenReturn(Optional.empty());
        ObjectNode data = service();
        data.put("typeAppealId", "00000000-0000-0000-0000-000000000000");
        data.remove("code");

        mapper.apply(data);

        assertThat(saved().externalAppealTypeId()).isNull();
        assertThat(saved().code()).isNull();
    }

    @Test
    void knownService_takesTheLastSnapshotAndTheContract() {
        LpuContract previousContract = mock(LpuContract.class);
        var oldDetails = new ContragentService.Details(null, null, "Старое название", false);
        ContragentService known = ContragentService.create(SERVICE, previousContract, oldDetails, EARLIER);
        when(contractRepository.findByExternalId(CONTRACT)).thenReturn(Optional.of(contract));
        when(repository.findByExternalId(SERVICE)).thenReturn(Optional.of(known));
        ObjectNode data = service();
        data.put("deleteMark", true);

        ImportResult result = mapper.apply(data);

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(saved()).isSameAs(known);
        assertThat(known.contract()).isSameAs(contract);
        assertThat(known.name()).isEqualTo("Лечебный массаж");
        assertThat(known.isArchive()).isTrue();
        assertThat(known.createdAt()).isEqualTo(EARLIER);
        assertThat(known.updatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ownerId", "id", "name"})
    void messageWithoutRequiredField_isContractViolation_andNothingIsSaved(String field) {
        when(contractRepository.findByExternalId(CONTRACT)).thenReturn(Optional.of(contract));
        ObjectNode data = service();
        data.remove(field);

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" " + field);
        verify(repository, never()).save(any());
    }

    @Test
    void serviceWithoutGuidUnderForeignContract_isSkipped_becauseTheContractIsCheckedFirst() {
        when(contractRepository.findByExternalId(CONTRACT)).thenReturn(Optional.empty());
        when(foreignRepository.existsByExternalIdAndKind(CONTRACT, ParentKind.LPU_CONTRACT)).thenReturn(true);
        ObjectNode data = service();
        data.remove("id");

        ImportResult result = mapper.apply(data);

        assertThat(result).isEqualTo(ImportResult.skipped("услуга по договору вне справочника: " + CONTRACT));
        verify(foreignRepository, never()).remember(any(), anyString(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void messageWithoutSeveralRequiredFields_namesTheContractFirst() {
        ObjectNode data = service();
        data.remove("name");
        data.remove("id");
        data.remove("ownerId");

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" ownerId");
        verify(repository, never()).save(any());
    }

    private ContragentService saved() {
        ArgumentCaptor<ContragentService> saved = ArgumentCaptor.forClass(ContragentService.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private static ObjectNode service() {
        ObjectNode data = JSON.createObjectNode();
        data.put("id", SERVICE.toString());
        data.put("ownerId", CONTRACT.toString());
        data.put("name", "Лечебный массаж");
        data.put("code", "41.178.178 ");
        data.put("typeAppealId", APPEAL_TYPE.toString());
        data.put("deleteMark", false);
        return data;
    }
}
