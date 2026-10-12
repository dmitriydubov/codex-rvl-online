package ru.projectx.datareference.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.PersonalDataCutoff;
import ru.projectx.datareference.entity.Policy;

class PolicyResponseTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final LocalDate BIRTH = LocalDate.of(1990, 1, 1);
    private static final LocalDate BEGIN = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void of_putsEveryPolicyValueIntoItsOwnField() {
        Policy policy = policy(new Policy.Details(UUID.randomUUID(), "П-0001", "Тестов", "Макет", "Заглушкович",
                BIRTH, "Мужской", "test@example.org", BEGIN, END, true, false));

        PolicyResponse response = PolicyResponse.of(policy, null, Map.of());

        assertThat(response).isEqualTo(new PolicyResponse(policy.id(), policy.externalId(), "П-0001",
                "Тестов", "Макет", "Заглушкович", BIRTH, "Мужской", "test@example.org", BEGIN, END, true, false,
                false, null, List.of()));
    }

    @Test
    void of_unavailableArchivedPolicy_keepsBothFlagsAndEmptyOptionalValues() {
        Policy policy = policy(new Policy.Details(UUID.randomUUID(), "П-0002", "Тестов", "Макет", null,
                BIRTH, null, null, BEGIN, null, false, true));

        PolicyResponse response = PolicyResponse.of(policy, null, Map.of());

        assertThat(response).isEqualTo(new PolicyResponse(policy.id(), policy.externalId(), "П-0002",
                "Тестов", "Макет", null, BIRTH, null, null, BEGIN, null, false, true, false, null, List.of()));
    }

    @Test
    void of_policyWithErasedPersonalData_hasEmptyNameBirthDateEmailAndTheMark() {
        Policy policy = policy(new Policy.Details(UUID.randomUUID(), "П-0004", "Тестов", "Макет", "Заглушкович",
                BIRTH, "Мужской", "test@example.org", BEGIN, END, true, false));
        policy.erasePersonalDataBeyond(new PersonalDataCutoff(END.plusDays(1), NOW), NOW);

        PolicyResponse response = PolicyResponse.of(policy, null, Map.of());

        assertThat(response).isEqualTo(new PolicyResponse(policy.id(), policy.externalId(), "П-0004",
                null, null, null, null, "Мужской", null, BEGIN, END, true, false, true, null, List.of()));
    }

    @Test
    void searchItem_putsNumberAndFullNameIntoTheirOwnFields() {
        Policy policy = policy(new Policy.Details(UUID.randomUUID(), "П-0003", "Тестов", "Макет", "Заглушкович",
                BIRTH, null, null, BEGIN, null, true, false));

        PolicySearchResponse response = PolicySearchResponse.of(List.of(policy));

        assertThat(response.items()).containsExactly(new PolicySearchResponse.Item(policy.id(), policy.externalId(),
                "П-0003", "Тестов", "Макет", "Заглушкович"));
    }

    private static Policy policy(Policy.Details details) {
        return Policy.create(UUID.randomUUID(), new Policy.Snapshot(details, List.of(), List.of()), NOW);
    }
}
