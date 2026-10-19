package ru.projectx.datareference.mapper.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import ru.projectx.datareference.config.PolicyContractProperties;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyProgram;
import ru.projectx.datareference.entity.PolicyServiceLine;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.support.ImportSettings;
import ru.projectx.datareference.support.TestApiProperties;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class PolicyMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String EMAIL_PROPERTY = "Адрес элпочты застрахованного";
    private static final UUID POLICY = UUID.fromString("1565b7e0-815d-11f1-bced-0894ef5d441c");
    private static final UUID CONTRACT = UUID.fromString("697501fc-80f9-11f1-8116-005056b40260");
    private static final UUID PROGRAM = UUID.fromString("f81c6e14-b493-4ded-9745-0771939f4425");
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);

    private final PolicyRepository repository = mock(PolicyRepository.class);
    private final PolicyMapper mapper = new PolicyMapper(
            repository, new PolicyContractProperties(EMAIL_PROPERTY), Clock.fixed(NOW, ZoneOffset.UTC),
            new PolicyPersonalDataTerm(ImportSettings.defaults().build(), TestApiProperties.defaults()));

    @Test
    void streamIsPolicies() {
        assertThat(mapper.stream()).isEqualTo(OneCStream.POLICIES);
    }

    @Test
    void newPolicy_isCreatedFromTheMessage() {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());

        ImportResult result = mapper.apply(policy());

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        Policy saved = saved();
        assertThat(saved.externalId()).isEqualTo(POLICY);
        assertThat(saved.details().externalContractId()).isEqualTo(CONTRACT);
        assertThat(saved.details().number()).isEqualTo("26 KM R/000001/1");
        assertThat(saved.details().lastName()).isEqualTo("Тестов");
        assertThat(saved.details().firstName()).isEqualTo("Пётр");
        assertThat(saved.details().middleName()).isEqualTo("Иванович");
        assertThat(saved.details().dateOfBirth()).isEqualTo(LocalDate.of(1980, 1, 2));
        assertThat(saved.details().gender()).isEqualTo("Мужской");
        assertThat(saved.details().email()).isEqualTo("petr@example.org");
        assertThat(saved.details().beginDate()).isEqualTo(LocalDate.of(2026, 7, 17));
        assertThat(saved.details().endDate()).isEqualTo(LocalDate.of(2027, 5, 17));
        assertThat(saved.details().availability()).isTrue();
        assertThat(saved.isArchive()).isFalse();
        assertThat(saved.createdAt()).isEqualTo(NOW);
    }

    @Test
    void knownPolicy_takesTheLastSnapshot_withBothTableParts() {
        Policy known = knownPolicy();
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.of(known));
        ObjectNode data = policy();
        data.put("deleteMark", true);

        ImportResult result = mapper.apply(data);

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(saved()).isSameAs(known);
        assertThat(known.details().lastName()).isEqualTo("Тестов");
        assertThat(known.programs()).hasSize(1);
        assertThat(known.serviceLines()).hasSize(1);
        assertThat(known.isArchive()).isTrue();
        assertThat(known.createdAt()).isEqualTo(EARLIER);
        assertThat(known.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void validityPeriod_comesFromTheActiveHeaderRowWithTheHighestNumber() {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());
        ObjectNode data = policy();
        ArrayNode header = data.putArray("header");
        headerRow(header, 1, true, "2024-01-01T00:00:00+03:00", "2024-12-31T00:00:00+03:00");
        headerRow(header, 2, true, "2025-01-01T00:00:00+03:00", "2025-12-31T00:00:00+03:00");
        headerRow(header, 3, false, "2026-01-01T00:00:00+03:00", "2026-12-31T00:00:00+03:00");

        mapper.apply(data);

        assertThat(saved().details().beginDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(saved().details().endDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(saved().details().availability()).isTrue();
    }

    @Test
    void policyWithoutActiveHeaderRows_takesTheRowWithTheHighestNumber() {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());
        ObjectNode data = policy();
        ArrayNode header = data.putArray("header");
        headerRow(header, 1, false, "2024-01-01T00:00:00+03:00", "2024-12-31T00:00:00+03:00");
        headerRow(header, 2, false, "2025-01-01T00:00:00+03:00", "3000-01-01T00:00:00+03:00");

        mapper.apply(data);

        assertThat(saved().details().beginDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(saved().details().endDate()).isNull();
        assertThat(saved().details().availability()).isFalse();
    }

    @Test
    void onlyHeaderRowNumberBeyondInt_isRejectedNamingField() {
        ObjectNode data = policy();
        headerRowNumbered(data.putArray("header"), 2147483648L, true);

        assertRejectedForHeaderRowNumber(data);
    }

    @Test
    void inactiveHeaderRowNumberBeyondInt_isRejectedNamingField() {
        ObjectNode data = policy();
        ArrayNode header = data.putArray("header");
        headerRow(header, 1, true, "2024-01-01T00:00:00+03:00", "2024-12-31T00:00:00+03:00");
        headerRowNumbered(header, -2147483649L, false);

        assertRejectedForHeaderRowNumber(data);
    }

    @Test
    void policyWithoutHeaderRows_isContractViolation() {
        ObjectNode data = policy();
        data.putArray("header");

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageContaining("header");
        verify(repository, never()).save(any());
    }

    @Test
    void email_isTheValueOfTheConfiguredProperty_only() {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());
        ObjectNode data = policy();
        ArrayNode properties = data.putArray("extProperties");
        property(properties, "Телефон застрахованного", "+70000000000");
        property(properties, EMAIL_PROPERTY, "petr@example.org");

        mapper.apply(data);

        assertThat(saved().details().email()).isEqualTo("petr@example.org");
    }

    @Test
    void policyWithoutEmailProperty_isStoredWithoutEmail() {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());
        ObjectNode data = policy();
        property(data.putArray("extProperties"), "Телефон застрахованного", "+70000000000");

        mapper.apply(data);

        assertThat(saved().details().email()).isNull();
    }

    @Test
    void programRow_isStoredWithItsAmounts() {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());

        mapper.apply(policy());

        PolicyProgram program = saved().programs().getFirst();
        assertThat(program.externalProgramId()).isEqualTo(PROGRAM);
        assertThat(program.beginDate()).isEqualTo(LocalDate.of(2026, 7, 17));
        assertThat(program.endDate()).isEqualTo(LocalDate.of(2027, 5, 17));
        assertThat(program.amount()).isEqualByComparingTo("12000000");
        assertThat(program.amountD()).isEqualByComparingTo("1000.50");
        assertThat(program.premium()).isEqualByComparingTo("58438.38");
        assertThat(program.premiumD()).isEqualByComparingTo("0");
        assertThat(program.limitAmount()).isEqualByComparingTo("11000000");
        assertThat(program.availability()).isTrue();
    }

    @Test
    void programRowWithoutOptionalAmounts_isStoredWithoutThem() {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());
        ObjectNode data = policy();
        ObjectNode row = (ObjectNode) data.path("programms").get(0);
        List.of("amount", "amountD", "premium", "premiumD", "endDate").forEach(row::remove);

        mapper.apply(data);

        PolicyProgram program = saved().programs().getFirst();
        assertThat(program.amount()).isNull();
        assertThat(program.amountD()).isNull();
        assertThat(program.premium()).isNull();
        assertThat(program.premiumD()).isNull();
        assertThat(program.endDate()).isNull();
    }

    @Test
    void serviceLine_isStoredWithItsConditions() {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());

        mapper.apply(policy());

        PolicyServiceLine line = saved().serviceLines().getFirst();
        assertThat(line.externalProgramId()).isEqualTo(PROGRAM);
        assertThat(line.layerNumber()).isEqualTo(2);
        assertThat(line.copayment()).isEqualByComparingTo(new BigDecimal("15.50"));
        assertThat(line.beginDate()).isEqualTo(LocalDate.of(2026, 7, 17));
        assertThat(line.endDate()).isEqualTo(LocalDate.of(2027, 5, 17));
        assertThat(line.availability()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "100.00", "100"})
    void copaymentAtRangeBound_isStored(String sent) {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());
        ObjectNode data = policy();
        serviceLine(data).put("copayment", new BigDecimal(sent));

        mapper.apply(data);

        assertThat(saved().serviceLines().getFirst().copayment()).isEqualByComparingTo(sent);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "100.01", "999.99", "1000.00", "-1000.00", "1E+3", "1e500", "1e999999999"})
    void copaymentOutOfRange_isContractViolationNamingFieldButNotValue(String sent) {
        ObjectNode data = policy();
        serviceLine(data).put("copayment", new BigDecimal(sent));

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле copayment вне диапазона от 0 до 100");
        verify(repository, never()).save(any());
    }

    @Test
    void copaymentBreakingScaleAndRange_isReportedByScale() {
        ObjectNode data = policy();
        serviceLine(data).put("copayment", new BigDecimal("100.001"));

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле copayment содержит больше двух знаков после запятой");
        verify(repository, never()).save(any());
    }

    @Test
    void copaymentAbsentOrNull_isMissingRequiredField() {
        ObjectNode absent = policy();
        serviceLine(absent).remove("copayment");
        ObjectNode nullValue = policy();
        serviceLine(nullValue).putNull("copayment");

        assertThatThrownBy(() -> mapper.apply(absent))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля copayment");
        assertThatThrownBy(() -> mapper.apply(nullValue))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля copayment");
        verify(repository, never()).save(any());
    }

    @Test
    void copaymentOfOtherJsonType_isNotANumber() {
        ObjectNode text = policy();
        serviceLine(text).put("copayment", "0");
        ObjectNode flag = policy();
        serviceLine(flag).put("copayment", true);

        assertThatThrownBy(() -> mapper.apply(text))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле copayment не является числом");
        assertThatThrownBy(() -> mapper.apply(flag))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле copayment не является числом");
        verify(repository, never()).save(any());
    }

    @Test
    void serviceLineWithEmptyReferences_isStoredWithoutThem() {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());
        ObjectNode data = policy();
        ObjectNode row = (ObjectNode) data.path("services").get(0);
        row.put("idProgramm", "00000000-0000-0000-0000-000000000000");
        row.put("beginDate", "0001-01-01T00:00:00+00:00");
        row.put("endDate", "0001-01-01T00:00:00+00:00");

        mapper.apply(data);

        PolicyServiceLine line = saved().serviceLines().getFirst();
        assertThat(line.externalProgramId()).isNull();
        assertThat(line.beginDate()).isNull();
        assertThat(line.endDate()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "idContract", "number", "lastName", "firstName", "dateOfBirth"})
    void policyWithoutRequiredField_isContractViolation_andNothingIsSaved(String field) {
        ObjectNode data = policy();
        data.remove(field);

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" " + field);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"idProgramm", "beginDate", "limit"})
    void programRowWithoutRequiredField_isContractViolation(String field) {
        ObjectNode data = policy();
        ((ObjectNode) data.path("programms").get(0)).remove(field);

        assertThatThrownBy(() -> mapper.apply(data)).isInstanceOf(OneCFieldException.class);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"layerNumber", "copayment"})
    void serviceLineWithoutRequiredField_isContractViolation(String field) {
        ObjectNode data = policy();
        ((ObjectNode) data.path("services").get(0)).remove(field);

        assertThatThrownBy(() -> mapper.apply(data)).isInstanceOf(OneCFieldException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void contractViolation_namesTheFieldButNotTheValue() {
        ObjectNode data = policy();
        data.put("dateOfBirth", "0001-01-01T00:00:00+00:00");
        data.remove("lastName");

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageNotContaining("Пётр")
                .hasMessageNotContaining("1980");
    }

    @Test
    void messageWithoutSeveralRequiredFields_namesTheGuidFirst() {
        ObjectNode data = policy();
        data.remove("lastName");
        data.putArray("header");
        data.remove("id");

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" id");
        verify(repository, never()).save(any());
    }

    private Policy saved() {
        ArgumentCaptor<Policy> saved = ArgumentCaptor.forClass(Policy.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private static Policy knownPolicy() {
        var details = new Policy.Details(CONTRACT, "26 KM R/000001/1", "Прежняя", "Анна", null,
                LocalDate.of(1980, 1, 2), null, null, LocalDate.of(2025, 1, 1), null, true, false);
        return Policy.create(POLICY, new Policy.Snapshot(details, List.of(), List.of()), EARLIER);
    }

    private static ObjectNode policy() {
        ObjectNode data = JSON.createObjectNode();
        data.put("id", POLICY.toString());
        data.put("number", "26 KM R/000001/1");
        data.put("idContract", CONTRACT.toString());
        data.put("lastName", "Тестов");
        data.put("firstName", "Пётр");
        data.put("middleName", "Иванович");
        data.put("dateOfBirth", "1980-01-02T00:00:00+03:00");
        data.put("gender", "Мужской");
        data.put("deleteMark", false);
        headerRow(data.putArray("header"), 1, true, "2026-07-17T00:00:00+03:00", "2027-05-17T00:00:00+03:00");
        programRow(data.putArray("programms"));
        serviceRow(data.putArray("services"));
        property(data.putArray("extProperties"), EMAIL_PROPERTY, "petr@example.org");
        return data;
    }

    private void assertRejectedForHeaderRowNumber(ObjectNode data) {
        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле rowNumber больше допустимого");
        verify(repository, never()).save(any());
    }

    private static void headerRowNumbered(ArrayNode header, long number, boolean available) {
        header.addObject()
                .put("rowNumber", number)
                .put("availability", available)
                .put("beginDate", "2025-01-01T00:00:00+03:00")
                .put("endDate", "2025-12-31T00:00:00+03:00");
    }

    private static void headerRow(ArrayNode header, int number, boolean available, String begin, String end) {
        header.addObject()
                .put("rowNumber", number)
                .put("availability", available)
                .put("beginDate", begin)
                .put("endDate", end);
    }

    private static void programRow(ArrayNode programs) {
        programs.addObject()
                .put("rowNumber", 1)
                .put("idProgramm", PROGRAM.toString())
                .put("beginDate", "2026-07-17T00:00:00+03:00")
                .put("endDate", "2027-05-17T00:00:00+03:00")
                .put("amount", 12000000)
                .put("amountD", new BigDecimal("1000.50"))
                .put("premium", new BigDecimal("58438.38"))
                .put("premiumD", 0)
                .put("limit", 11000000)
                .put("availability", true);
    }

    private static void serviceRow(ArrayNode services) {
        services.addObject()
                .put("rowNumber", 1)
                .put("layerNumber", 2)
                .put("copayment", new BigDecimal("15.50"))
                .put("idProgramm", PROGRAM.toString())
                .put("beginDate", "2026-07-17T00:00:00+03:00")
                .put("endDate", "2027-05-17T00:00:00+03:00")
                .put("availability", true);
    }

    private static ObjectNode serviceLine(ObjectNode data) {
        return (ObjectNode) data.path("services").get(0);
    }

    private static void property(ArrayNode properties, String name, String value) {
        properties.addObject()
                .put("propertyName", name)
                .putObject("value")
                .put("type", "string")
                .put("value", value);
    }
}
