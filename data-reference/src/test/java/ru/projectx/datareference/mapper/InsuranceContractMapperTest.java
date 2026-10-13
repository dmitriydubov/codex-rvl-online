package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class InsuranceContractMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DMS = "ЛичноеСтрахование";
    private static final String DMS_AS_SPECIFIED = "Личное страхование";
    private static final String INSURER_ROLE = "Страхователь";
    private static final UUID CONTRACT = UUID.fromString("75af8e16-0000-4000-8000-0000000000c1");
    private static final UUID INSURER = UUID.fromString("5e595002-0000-4000-8000-0000000000d1");
    private static final UUID INSURED = UUID.fromString("5e595002-0000-4000-8000-0000000000d2");
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);

    private final InsuranceContractRepository repository = mock(InsuranceContractRepository.class);
    private final InsuranceContractMapper mapper = mapper(List.of(DMS, DMS_AS_SPECIFIED), INSURER_ROLE);

    @Test
    void streamIsInsuranceContracts() {
        assertThat(mapper.stream()).isEqualTo(OneCStream.INSURANCE_CONTRACTS);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void withoutDmsProductSetting_everyContractIsSkipped(String dmsProduct) {
        ImportResult result = mapper(Arrays.asList(dmsProduct), INSURER_ROLE)
                .apply(contract(DMS, party(INSURER, "Страхователь")));

        assertThat(result).isEqualTo(ImportResult.skipped("не задан projectx.onec.contract.dms-product-type"
                + " — отличить ДМС от прочих продуктов страхования нечем"));
        verifyNoInteractions(repository);
    }

    @Test
    void emptyDmsProductSetting_skipsEveryContract() {
        ImportResult result = mapper(List.of(), INSURER_ROLE).apply(contract(DMS, party(INSURER, "Страхователь")));

        assertThat(result).isEqualTo(ImportResult.skipped("не задан projectx.onec.contract.dms-product-type"
                + " — отличить ДМС от прочих продуктов страхования нечем"));
    }

    @ParameterizedTest
    @ValueSource(strings = {DMS, DMS_AS_SPECIFIED})
    void dmsContract_isAppliedInEitherSpelling(String productType) {
        ImportResult result = mapper.apply(contract(productType, party(INSURER, "Страхователь")));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(saved().externalId()).isEqualTo(CONTRACT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"личное страхование", "Личное  страхование", "личноестрахование", "ЛИЧНОЕСТРАХОВАНИЕ"})
    void otherSpellingOfDms_isSkippedNamingTheSpelling(String productType) {
        ImportResult result = mapper.apply(contract(productType, party(INSURER, "Страхователь")));

        assertThat(result).isEqualTo(ImportResult.skipped("договор не ДМС: тип продукта " + productType));
        verifyNoInteractions(repository);
    }

    @Test
    void onlyTheConfiguredSpelling_isDms() {
        ImportResult result = mapper(List.of(DMS), INSURER_ROLE)
                .apply(contract(DMS_AS_SPECIFIED, party(INSURER, "Страхователь")));

        assertThat(result).isEqualTo(ImportResult.skipped("договор не ДМС: тип продукта Личное страхование"));
    }

    @Test
    void insurer_isPickedByTheConfiguredRole() {
        ObjectNode insurer = party(INSURER, "РольСтрахователя");

        mapper(List.of(DMS), "РольСтрахователя").apply(contract(DMS, party(INSURED, "Страхователь"), insurer));

        assertThat(saved().externalInsurerId()).isEqualTo(INSURER);
    }

    @Test
    void insurerRole_isComparedExactly() {
        mapper.apply(contract(DMS, party(INSURER, "страхователь"), party(INSURED, "Страхователь ")));

        assertThat(saved().externalInsurerId()).isNull();
    }

    @Test
    void contractOfAnotherProduct_isSkippedNamingTheProduct() {
        ImportResult result = mapper.apply(contract("Обязательное страхование", party(INSURER, "Страхователь")));

        assertThat(result).isEqualTo(ImportResult.skipped("договор не ДМС: тип продукта Обязательное страхование"));
        verifyNoInteractions(repository);
    }

    @Test
    void contractWithoutProduct_isSkipped_evenWithoutGuid() {
        ObjectNode data = contract(DMS, party(INSURER, "Страхователь"));
        data.remove("productType");
        data.remove("contractId");

        ImportResult result = mapper.apply(data);

        assertThat(result).isEqualTo(ImportResult.skipped("договор не ДМС: тип продукта null"));
    }

    @Test
    void newDmsContract_isCreatedFromTheMessage() {
        ImportResult result = mapper.apply(contract(" " + DMS + " ", party(INSURER, "Страхователь")));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        InsuranceContract saved = saved();
        assertThat(saved.externalId()).isEqualTo(CONTRACT);
        assertThat(saved.number()).isEqualTo("0000000001");
        assertThat(saved.beginDate()).isEqualTo(LocalDate.of(2026, 5, 7));
        assertThat(saved.endDate()).isEqualTo(LocalDate.of(2027, 5, 6));
        assertThat(saved.externalInsurerId()).isEqualTo(INSURER);
        assertThat(saved.isArchive()).isFalse();
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void insurer_isPickedByRoleAmongSeveralParties() {
        ObjectNode insurer = party(INSURER, "Выгодоприобретатель", "Страхователь");

        mapper.apply(contract(DMS, party(INSURED, "Застрахованный"), insurer));

        assertThat(saved().externalInsurerId()).isEqualTo(INSURER);
    }

    @Test
    void contractWithoutInsurerRole_isStoredWithoutInsurer() {
        mapper.apply(contract(DMS, party(INSURED, "Застрахованный")));

        assertThat(saved().externalInsurerId()).isNull();
    }

    @Test
    void emptyEndDate_isStoredAsOpen() {
        ObjectNode data = contract(DMS, party(INSURER, "Страхователь"));
        data.remove("dateEndISO");

        mapper.apply(data);

        assertThat(saved().endDate()).isNull();
    }

    @Test
    void knownContract_takesTheLastSnapshot() {
        var oldDetails = new InsuranceContract.Details("0000000000", LocalDate.of(2025, 1, 1), null, INSURED, false);
        InsuranceContract known = InsuranceContract.create(CONTRACT, oldDetails, EARLIER);
        when(repository.findByExternalId(CONTRACT)).thenReturn(Optional.of(known));
        ObjectNode data = contract(DMS, party(INSURER, "Страхователь"));
        data.put("notActive", true);

        ImportResult result = mapper.apply(data);

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(saved()).isSameAs(known);
        assertThat(known.number()).isEqualTo("0000000001");
        assertThat(known.externalInsurerId()).isEqualTo(INSURER);
        assertThat(known.isArchive()).isTrue();
        assertThat(known.createdAt()).isEqualTo(EARLIER);
        assertThat(known.updatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"contractId", "number", "dateBeginISO"})
    void dmsContractWithoutRequiredField_isContractViolation_andNothingIsSaved(String field) {
        ObjectNode data = contract(DMS, party(INSURER, "Страхователь"));
        data.remove(field);

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" " + field);
        verify(repository, never()).save(any());
    }

    private InsuranceContractMapper mapper(List<String> dmsProducts, String insurerRole) {
        var properties = new OneCContractProperties(null, null, dmsProducts, insurerRole, "Адрес", List.of());
        return new InsuranceContractMapper(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private InsuranceContract saved() {
        ArgumentCaptor<InsuranceContract> saved = ArgumentCaptor.forClass(InsuranceContract.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private static ObjectNode contract(String productType, ObjectNode... parties) {
        ObjectNode data = JSON.createObjectNode();
        data.put("contractId", CONTRACT.toString());
        data.put("number", " 0000000001");
        data.put("productType", productType);
        data.put("dateBeginISO", "2026-05-07T00:00:00");
        data.put("dateEndISO", "2027-05-06T23:59:59");
        data.put("notActive", false);
        ArrayNode list = data.putArray("contragentList");
        List.of(parties).forEach(list::add);
        return data;
    }

    private static ObjectNode party(UUID guid, String... roleIds) {
        ObjectNode party = JSON.createObjectNode();
        ArrayNode roles = party.putArray("rolesList");
        List.of(roleIds).forEach(roleId -> roles.addObject().put("roleName", roleId).put("roleId", roleId));
        party.putObject("contragent").put("type", "Юридическое лицо").put("guid", guid.toString());
        return party;
    }
}
