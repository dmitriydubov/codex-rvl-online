package ru.projectx.datareference.mapper.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import ru.projectx.datareference.config.PolicyContractProperties;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.support.ImportSettings;
import ru.projectx.datareference.support.TestApiProperties;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PolicyMapperPersonalDataTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID POLICY = UUID.fromString("1565b7e0-815d-11f1-bced-0894ef5d441c");
    private static final Instant TERM_ENDED_AND_GRACE_PASSED = Instant.parse("2026-03-02T21:00:00Z");
    private static final Instant GRACE_NOT_PASSED = Instant.parse("2026-03-02T20:59:59Z");

    private final PolicyRepository repository = mock(PolicyRepository.class);

    @Test
    void newPolicyBeyondTerm_isSavedWithoutPersonalData() {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());

        mapperAt(TERM_ENDED_AND_GRACE_PASSED).apply(policyEnding("2026-01-31"));

        Policy saved = saved();
        assertThat(saved.personalDataErased()).isTrue();
        assertThat(saved.details().lastName()).isNull();
        assertThat(saved.details().number()).isEqualTo("П-0001");
    }

    @Test
    void newPolicyWithinTerm_keepsPersonalData() {
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.empty());

        mapperAt(GRACE_NOT_PASSED).apply(policyEnding("2026-01-31"));

        assertThat(saved().details().lastName()).isEqualTo("Выдумкин");
        assertThat(saved().personalDataErased()).isFalse();
    }

    @Test
    void knownPolicyResentBeyondTerm_staysWithoutPersonalData() {
        Policy known = knownPolicyEnding(LocalDate.of(2026, 1, 31));
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.of(known));

        mapperAt(TERM_ENDED_AND_GRACE_PASSED).apply(policyEnding("2026-01-31"));

        assertThat(saved()).isSameAs(known);
        assertThat(known.personalDataErased()).isTrue();
        assertThat(known.details().dateOfBirth()).isNull();
    }

    @Test
    void knownPolicyExtendedByTheMessage_keepsPersonalDataFromIt() {
        Policy known = knownPolicyEnding(LocalDate.of(2026, 1, 31));
        when(repository.findByExternalId(POLICY)).thenReturn(Optional.of(known));

        mapperAt(TERM_ENDED_AND_GRACE_PASSED).apply(policyEnding("2026-12-31"));

        assertThat(known.personalDataErased()).isFalse();
        assertThat(known.details().lastName()).isEqualTo("Выдумкин");
    }

    private PolicyMapper mapperAt(Instant now) {
        return new PolicyMapper(repository, new PolicyContractProperties("почта"), Clock.fixed(now, ZoneOffset.UTC),
                new PolicyPersonalDataTerm(ImportSettings.defaults().build(), TestApiProperties.defaults()));
    }

    private Policy saved() {
        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static Policy knownPolicyEnding(LocalDate endDate) {
        var details = new Policy.Details(UUID.randomUUID(), "П-0001", "Старый", "Тест", null, LocalDate.of(1990, 1, 1),
                null, null, LocalDate.of(2026, 1, 1), endDate, true, false);
        return Policy.create(POLICY, new Policy.Snapshot(details, List.of(), List.of()),
                Instant.parse("2026-01-10T09:00:00Z"));
    }

    private static ObjectNode policyEnding(String endDate) {
        ObjectNode data = JSON.createObjectNode()
                .put("id", POLICY.toString())
                .put("idContract", "697501fc-80f9-11f1-8116-005056b40260")
                .put("number", "П-0001")
                .put("lastName", "Выдумкин")
                .put("firstName", "Тест")
                .put("dateOfBirth", "1990-01-01")
                .put("deleteMark", false);
        data.putArray("header").addObject()
                .put("rowNumber", 1)
                .put("beginDate", "2026-01-01")
                .put("endDate", endDate)
                .put("availability", true);
        return data;
    }
}
